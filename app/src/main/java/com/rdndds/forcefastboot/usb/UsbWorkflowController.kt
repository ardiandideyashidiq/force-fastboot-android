package com.rdndds.forcefastboot.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.rdndds.forcefastboot.FastbootProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the complete MTK USB workflow: attach/detach classification, permission
 * requests, the preloader FASTBOOT burst loop, and automatic fastboot getvar
 * queries.
 *
 * All entry points must be called from the main thread. Long-running work is
 * launched into [scope]; every call that touches Compose snapshot state hops
 * back to Dispatchers.Main first.
 */
class UsbWorkflowController(
    context: Context,
    private val scope: CoroutineScope,
    private val logFn: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val usbManager =
        appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private var lastProcessedDeviceId: Int? = null
    private var lastProcessedTimestamp: Long = 0L
    private var lastPermissionRequestedDeviceId: Int? = null

    @Volatile
    private var lastGetVarDeviceId: Int? = null
    private var fastbootHandledDeviceId: Int? = null
    private var burstJob: Job? = null
    private var getvarJob: Job? = null

    companion object {
        const val ACTION_USB_PERMISSION = "com.rdndds.forcefastboot.USB_PERMISSION"

        private const val TAG = "MTKUSB"
        private const val DEDUP_WINDOW_MS = 2000L
        private const val SETTLE_DELAY_MS = 500L
        private const val GETVAR_PAUSE_MS = 300L
        private const val GETVAR_OPEN_RETRIES = 4
        private const val GETVAR_RETRY_DELAY_MS = 250L
        private const val BURST_INTERVAL_MS = 50L
        private const val RECONNECT_WAIT_MS = 5000L
        private const val RECONNECT_POLL_MS = 50L
        private const val BAUD_RATE = 115200
        private const val SERIAL_WRITE_TIMEOUT_MS = 200
        private const val SERIAL_READ_TIMEOUT_MS = 100
    }

    /** Scan for any MTK devices already connected on app launch or resume. */
    fun scanConnectedDevices() {
        for (device in usbManager.deviceList.values) {
            if (device.vendorId == MtkUsbIds.VID) {
                onDeviceAttached(device)
            }
        }
    }

    fun onDeviceAttached(device: UsbDevice) {
        val isPreloader = device.isPreloader()
        val isFastboot = device.isMtkFastboot()

        if (!isPreloader && !isFastboot) return

        val now = System.currentTimeMillis()
        if (device.deviceId == lastProcessedDeviceId &&
            now - lastProcessedTimestamp < DEDUP_WINDOW_MS
        ) {
            return
        }
        lastProcessedDeviceId = device.deviceId
        lastProcessedTimestamp = now

        if (isFastboot) {
            if (device.deviceId == fastbootHandledDeviceId) return
            handleFastbootAttached(device)
            return
        }

        // Skip preloader handling silently while spam loop is active
        if (burstJob?.isActive == true) {
            return
        }

        logDeviceEvent("ATTACHED", device)

        if (usbManager.hasPermission(device)) {
            logFn("[INFO] Permission cached, sending FASTBOOT burst...")
            sendFastbootSpam(device)
        } else {
            // Some MTK preloader devices allow openDevice() without a permission
            // grant (the kernel driver is not yet bound). If that works, skip the
            // permission dialog and start the burst immediately.
            val testConn = usbManager.openDevice(device)
            if (testConn != null) {
                testConn.close()
                logFn("[INFO] Device opened directly, sending FASTBOOT burst...")
                sendFastbootSpam(device)
            } else if (device.deviceId != lastPermissionRequestedDeviceId) {
                requestUsbPermission(device)
            }
        }
    }

    fun onDeviceDetached(device: UsbDevice) {
        if (device.isPreloader() || device.isMtkFastboot()) {
            logDeviceEvent("DETACHED", device)
        }

        if (device.vendorId != MtkUsbIds.VID) return

        // Reset tracking so any subsequent device connection is fully processed
        lastProcessedDeviceId = null
        lastPermissionRequestedDeviceId = null
        lastGetVarDeviceId = null
        fastbootHandledDeviceId = null
        burstJob?.cancel()
        burstJob = null
        getvarJob?.cancel()
        getvarJob = null
    }

    fun onPermissionResult(
        granted: Boolean,
        device: UsbDevice?,
    ) {
        lastPermissionRequestedDeviceId = null

        if (granted && device != null) {
            logFn("[INFO] Permission granted via dialog")
            if (device.isPreloader()) {
                sendFastbootSpam(device)
            } else if (device.hasFastbootInterface()) {
                runAutoFastbootGetVars(device)
            }
        } else {
            logFn("[INFO] Permission denied or dialog dismissed")
        }
    }

    /** Cancel all jobs and clear tracking state so the next device is fully processed. */
    fun reset() {
        burstJob?.cancel()
        burstJob = null
        getvarJob?.cancel()
        getvarJob = null
        lastProcessedDeviceId = null
        lastPermissionRequestedDeviceId = null
        lastGetVarDeviceId = null
        fastbootHandledDeviceId = null
    }

    private fun requestUsbPermission(device: UsbDevice) {
        lastPermissionRequestedDeviceId = device.deviceId
        logFn("[INFO] Requesting USB permission dialog...")
        // Android 12+ requires FLAG_MUTABLE for UsbManager to populate the
        // PendingIntent with the device extra. Older APIs default to mutable.
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        val intent =
            Intent(ACTION_USB_PERMISSION).apply {
                setPackage(appContext.packageName)
            }
        val pi = PendingIntent.getBroadcast(appContext, 0, intent, flags)
        usbManager.requestPermission(device, pi)
    }

    /** Handle fastboot device arrival: log success, cancel spam loop, query getvars. */
    private fun handleFastbootAttached(device: UsbDevice) {
        fastbootHandledDeviceId = device.deviceId
        logDeviceEvent("ATTACHED", device)
        logFn("[SUCCESS] Device is now in FASTBOOT mode!")

        burstJob?.cancel()
        burstJob = null

        if (usbManager.hasPermission(device)) {
            runAutoFastbootGetVars(device)
        } else {
            scope.launch(Dispatchers.Main) {
                // Wait for system UI / USB chooser transitions to settle
                delay(SETTLE_DELAY_MS)
                if (usbManager.hasPermission(device)) {
                    runAutoFastbootGetVars(device)
                } else if (device.deviceId != lastPermissionRequestedDeviceId) {
                    requestUsbPermission(device)
                }
            }
        }
    }

    /** Automatically query current-slot and version-bootloader once in Fastboot mode. */
    private fun runAutoFastbootGetVars(device: UsbDevice? = null) {
        val targetDeviceId = device?.deviceId
        if (targetDeviceId != null && lastGetVarDeviceId == targetDeviceId) {
            return
        }

        getvarJob?.cancel()
        getvarJob =
            scope.launch(Dispatchers.IO) {
                // Brief pause to allow kernel USB descriptors to stabilize
                delay(GETVAR_PAUSE_MS)
                logMain("[INFO] Fastboot device ready. Querying current-slot and version-bootloader...")

                var protoPair: Pair<UsbDevice, FastbootProtocol>? = null
                for (retry in 1..GETVAR_OPEN_RETRIES) {
                    if (!isActive) return@launch
                    protoPair = FastbootProtocol.open(usbManager)
                    if (protoPair != null) break
                    delay(GETVAR_RETRY_DELAY_MS)
                }

                if (protoPair == null) {
                    logMain("[GETVAR] Could not open fastboot interface")
                    return@launch
                }

                val (fastbootDev, proto) = protoPair
                lastGetVarDeviceId = targetDeviceId ?: fastbootDev.deviceId

                proto.use {
                    for (v in listOf("current-slot", "version-bootloader")) {
                        if (!isActive) break
                        logMain("[GETVAR] Querying: $v")
                        val responses = it.getVar(v)
                        logGetVarResponses(v, responses)
                    }
                }
            }
    }

    private suspend fun logGetVarResponses(
        name: String,
        responses: List<FastbootProtocol.Response>,
    ) {
        withContext(Dispatchers.Main) {
            for (resp in responses) {
                when (resp) {
                    is FastbootProtocol.Response.Info ->
                        logFn("[GETVAR] ${resp.message}")
                    is FastbootProtocol.Response.Okay ->
                        logFn("[GETVAR] $name = ${resp.data}")
                    is FastbootProtocol.Response.Fail ->
                        logFn("[GETVAR] ✗ FAIL: ${resp.message}")
                }
            }
        }
    }

    private fun logDeviceEvent(
        event: String,
        device: UsbDevice,
    ) {
        val vid = device.vendorId
        val pid = device.productId

        val mode = if (device.isPreloader()) "MTK PRELOADER" else "FASTBOOT"

        val logBuilder = StringBuilder()
        logBuilder.appendLine("[$event] $mode")
        logBuilder.appendLine("  -> Name: ${device.deviceName}")
        logBuilder.appendLine("  -> VID: 0x${vid.toString(16).uppercase()} ($vid) | PID: 0x${pid.toString(16).uppercase()} ($pid)")
        logBuilder.appendLine("  -> Interfaces: ${device.interfaceCount}")

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            logBuilder.appendLine(
                "    - Interface $i: class=${intf.interfaceClass}, subclass=${intf.interfaceSubclass}, protocol=${intf.interfaceProtocol}, endpoints=${intf.endpointCount}",
            )
            for (j in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(j)
                val dir = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
                val type =
                    when (ep.type) {
                        UsbConstants.USB_ENDPOINT_XFER_BULK -> "BULK"
                        UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "CONTROL"
                        UsbConstants.USB_ENDPOINT_XFER_INT -> "INTERRUPT"
                        else -> "UNKNOWN(${ep.type})"
                    }
                logBuilder.appendLine("      - EP $j: $dir $type maxPacket=${ep.maxPacketSize}")
            }
        }

        logFn(logBuilder.toString().trim())
    }

    private fun findDeviceByVidPid(
        vid: Int,
        pid: Int,
    ): UsbDevice? {
        for (device in usbManager.deviceList.values) {
            if (device.vendorId == vid && device.productId == pid) return device
        }
        return null
    }

    /**
     * Find any MTK device: first try the original preloader PID,
     * then look for any MTK device with fastboot interface descriptors.
     * This handles the PID change when the device switches from preloader to fastboot.
     */
    private fun findMtkDevice(
        vid: Int,
        preloaderPid: Int,
    ): UsbDevice? {
        findDeviceByVidPid(vid, preloaderPid)?.let { return it }
        for (device in usbManager.deviceList.values) {
            if (device.vendorId == vid && device.hasFastbootInterface()) return device
        }
        return null
    }

    /**
     * Optimized FASTBOOT spam burst loop:
     * Reuses the open CDC ACM port across burst attempts to eliminate 20-50ms per-iteration
     * USB claim/descriptor overhead, maximizing chances of catching the ~1.5s preloader window.
     */
    private fun sendFastbootSpam(device: UsbDevice) {
        burstJob?.cancel()
        val vid = device.vendorId
        val pid = device.productId
        burstJob =
            scope.launch(Dispatchers.IO) {
                logMain("[ACTION] Starting FASTBOOT burst loop...")

                var session: SerialPortSession? = null
                var attempt = 0

                try {
                    while (isActive) {
                        attempt++

                        val currentDevice = findMtkDevice(vid, pid)
                        if (currentDevice == null) {
                            val result = handleDeviceGone(session, vid, pid)
                            session = null
                            if (result == DeviceReconnectResult.STOPPED) break
                            continue
                        }

                        if (currentDevice.hasFastbootInterface()) {
                            withContext(Dispatchers.Main) {
                                onDeviceAttached(currentDevice)
                            }
                            break
                        }

                        logMain("[BURST $attempt] Sending FASTBOOT to PID 0x${pid.toString(16).uppercase()}")

                        session = findOrCreateSession(currentDevice, session)
                        if (session != null) {
                            val sent = attemptFastbootWrite(session)
                            if (!sent) session = null
                        } else {
                            logMain("[FAILED] Could not open preloader port")
                        }

                        delay(BURST_INTERVAL_MS)
                    }
                } finally {
                    session?.close()
                }
            }
    }

    private enum class DeviceReconnectResult { STOPPED, CONTINUE }

    /**
     * Handle the case where the device disappears during the burst loop.
     * Closes the session, waits for reconnect, and returns whether to stop or continue.
     */
    private suspend fun handleDeviceGone(
        session: SerialPortSession?,
        vid: Int,
        pid: Int,
    ): DeviceReconnectResult {
        session?.close()
        logMain("[INFO] Device gone, waiting for reconnect...")
        val retryDevice = waitForReconnect(vid, pid)
        if (retryDevice == null) {
            logMain("[INFO] Device not reconnected, stopping")
            return DeviceReconnectResult.STOPPED
        }
        if (retryDevice.hasFastbootInterface()) {
            withContext(Dispatchers.Main) {
                onDeviceAttached(retryDevice)
            }
            return DeviceReconnectResult.STOPPED
        }
        return DeviceReconnectResult.CONTINUE
    }

    /** Return the existing session if still open, otherwise open a new preloader session. */
    private fun findOrCreateSession(
        device: UsbDevice,
        currentSession: SerialPortSession?,
    ): SerialPortSession? = currentSession ?: openPreloaderSession(device)

    /**
     * Poll for the device coming back after a detach during the burst loop.
     * Returns the re-enumerated device or null once the wait budget expires.
     */
    private suspend fun waitForReconnect(
        vid: Int,
        pid: Int,
    ): UsbDevice? {
        var waited = 0
        while (waited < RECONNECT_WAIT_MS) {
            if (!currentCoroutineContext().isActive) break
            delay(RECONNECT_POLL_MS)
            waited += RECONNECT_POLL_MS.toInt()
            val reconnected = findMtkDevice(vid, pid)
            if (reconnected != null) {
                logMain("[INFO] Device reconnected!")
                break
            }
        }
        return findMtkDevice(vid, pid)
    }

    private fun openPreloaderSession(device: UsbDevice): SerialPortSession? {
        var conn: UsbDeviceConnection? = null
        var port: UsbSerialPort? = null
        try {
            val driver = CdcAcmSerialDriver(device)
            conn = usbManager.openDevice(device) ?: return null
            port = driver.ports[0]
            port.open(conn)
            // 8N1: 8 data bits, no parity, 1 stop bit — standard for MTK preloader serial
            port.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            return SerialPortSession(conn, port)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open serial port: ${e.message}")
            try {
                port?.close()
            } catch (_: Exception) {
            }
            try {
                conn?.close()
            } catch (_: Exception) {
            }
            return null
        }
    }

    /** Returns false when the write/read failed; closes [session] before returning. */
    private suspend fun attemptFastbootWrite(session: SerialPortSession): Boolean = try {
        val cmd = "FASTBOOT".toByteArray(Charsets.US_ASCII)
        session.port.write(cmd, SERIAL_WRITE_TIMEOUT_MS)

        // Preloader responds with "READY" (5 bytes) or no response at all
        val resp = ByteArray(64)
        val received = session.port.read(resp, SERIAL_READ_TIMEOUT_MS)
        val respStr =
            if (received > 0) {
                String(resp, 0, received, Charsets.US_ASCII).trim()
            } else {
                null
            }
        val msg =
            if (respStr != null) {
                "[SENT] FASTBOOT command sent → response: $respStr"
            } else {
                "[SENT] FASTBOOT command sent"
            }
        logMain(msg)
        true
    } catch (e: Exception) {
        Log.e(TAG, "send error: ${e.message}")
        try {
            session.close()
        } catch (_: Exception) {
        }
        logMain("[FAILED] Could not send FASTBOOT command")
        false
    }

    private suspend fun logMain(message: String) {
        withContext(Dispatchers.Main) { logFn(message) }
    }
}
