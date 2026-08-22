package com.rdndds.forcefastboot

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.rdndds.forcefastboot.usb.MtkUsbIds

/**
 * Implements the fastboot protocol over raw USB bulk transfers.
 *
 * The fastboot protocol is a simple synchronous host-driven protocol:
 * 1. Host sends an ASCII command on the OUT bulk endpoint (max 64 bytes)
 * 2. Device responds on the IN bulk endpoint with a 4-byte status prefix:
 *    - "OKAY" — success (optional data after prefix)
 *    - "FAIL" — failure (error message after prefix)
 *    - "INFO" — informational (message after prefix, read another response)
 *    - "DATA" — data transfer phase (8-char hex size after prefix)
 */
class FastbootProtocol(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val endpointIn: UsbEndpoint,
    private val endpointOut: UsbEndpoint,
) : AutoCloseable {
    private val timeout = TIMEOUT_MS

    sealed class Response {
        data class Okay(
            val data: String,
        ) : Response()

        data class Fail(
            val message: String,
        ) : Response()

        data class Info(
            val message: String,
        ) : Response()
    }

    /**
     * Send a raw fastboot command and collect all responses.
     * INFO responses are accumulated; the final OKAY/FAIL terminates.
     */
    fun command(cmd: String): List<Response> {
        val cmdBytes = cmd.toByteArray(Charsets.US_ASCII)
        val written = connection.bulkTransfer(endpointOut, cmdBytes, cmdBytes.size, timeout)
        if (written < 0) return listOf(Response.Fail("USB write failed"))

        val responses = mutableListOf<Response>()
        while (true) {
            val buf = ByteArray(BUFFER_SIZE)
            val read = connection.bulkTransfer(endpointIn, buf, buf.size, timeout)
            if (read < MIN_RESPONSE_PREFIX_BYTES) {
                responses.add(Response.Fail("Short read ($read bytes)"))
                break
            }

            val resp = parseResponseBuffer(buf, read)
            responses.add(resp)
            if (resp !is Response.Info) {
                break
            }
        }
        return responses
    }

    /** Convenience: send "getvar:<name>" and return the value or error. */
    fun getVar(name: String): List<Response> = command("getvar:$name")

    override fun close() {
        resetEndpoints()
        try {
            connection.releaseInterface(usbInterface)
        } catch (_: Exception) {
        }
        try {
            connection.close()
        } catch (_: Exception) {
        }
    }

    /**
     * Reset USB endpoint state before releasing the interface.
     *
     * MTK fastboot devices retain stale data-toggle / endpoint state after a
     * host session. If we just releaseInterface + close, the next host that
     * connects finds the bulk endpoints out of sync — getvar still works
     * (small single-packet exchanges re-sync naturally) but multi-packet DATA
     * transfers (flash/download) hang.
     *
     * Per USB 2.0 spec §5.8.5 and §9.4.10:
     *   - CLEAR_FEATURE(ENDPOINT_HALT) resets data toggle to DATA0
     *   - SET_INTERFACE(alt=0) resets all endpoints on the interface
     */
    private fun resetEndpoints() {
        try {
            val drain = ByteArray(BUFFER_SIZE)
            @Suppress("ControlFlowWithEmptyBody")
            while (connection.bulkTransfer(endpointIn, drain, drain.size, DRAIN_TIMEOUT) > 0) {
            }
        } catch (_: Exception) {
        }

        // CLEAR_FEATURE(ENDPOINT_HALT) on OUT endpoint — resets data toggle to DATA0
        try {
            connection.controlTransfer(
                USB_RECIP_ENDPOINT.toInt(), // bmRequestType: USB_DIR_OUT | USB_TYPE_STANDARD | USB_RECIP_ENDPOINT
                USB_REQ_CLEAR_FEATURE.toInt(), // bRequest: USB_REQ_CLEAR_FEATURE
                ENDPOINT_HALT.toInt(), // wValue: ENDPOINT_HALT
                endpointOut.address,
                null,
                0,
                CLEANUP_TIMEOUT,
            )
        } catch (_: Exception) {
        }

        // CLEAR_FEATURE(ENDPOINT_HALT) on IN endpoint — resets data toggle to DATA0
        try {
            connection.controlTransfer(
                USB_RECIP_ENDPOINT.toInt(), // bmRequestType: USB_DIR_OUT | USB_TYPE_STANDARD | USB_RECIP_ENDPOINT
                USB_REQ_CLEAR_FEATURE.toInt(), // bRequest: USB_REQ_CLEAR_FEATURE
                ENDPOINT_HALT.toInt(), // wValue: ENDPOINT_HALT
                endpointIn.address,
                null,
                0,
                CLEANUP_TIMEOUT,
            )
        } catch (_: Exception) {
        }

        try {
            connection.controlTransfer(
                USB_RECIP_INTERFACE.toInt(),
                USB_REQ_SET_INTERFACE.toInt(),
                0x00,
                usbInterface.id,
                null,
                0,
                CLEANUP_TIMEOUT,
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TIMEOUT_MS = 5000
        private const val BUFFER_SIZE = 512
        private const val MIN_RESPONSE_PREFIX_BYTES = 4
        private const val CLEANUP_TIMEOUT = 1000
        private const val DRAIN_TIMEOUT = 50

        // USB control transfer constants (per USB 2.0 spec §9.4)
        private const val USB_REQ_CLEAR_FEATURE: Byte = 0x01
        private const val USB_REQ_SET_INTERFACE: Byte = 0x0B
        private const val USB_RECIP_ENDPOINT: Byte = 0x02
        private const val USB_RECIP_INTERFACE: Byte = 0x01
        private const val ENDPOINT_HALT: Short = 0x00

        /**
         * Parse raw Fastboot response chunk.
         */
        fun parseResponseBuffer(
            buffer: ByteArray,
            length: Int,
        ): Response {
            if (length < MIN_RESPONSE_PREFIX_BYTES) {
                return Response.Fail("Short read ($length bytes)")
            }
            val status = String(buffer, 0, MIN_RESPONSE_PREFIX_BYTES, Charsets.US_ASCII)
            val payload =
                if (length >
                    MIN_RESPONSE_PREFIX_BYTES
                ) {
                    String(buffer, MIN_RESPONSE_PREFIX_BYTES, length - MIN_RESPONSE_PREFIX_BYTES, Charsets.US_ASCII).trim()
                } else {
                    ""
                }
            return when (status) {
                "OKAY" -> Response.Okay(payload)
                "FAIL" -> Response.Fail(payload)
                "INFO" -> Response.Info(payload)
                else -> Response.Fail("Unknown status: $status")
            }
        }

        /**
         * Find a fastboot-mode device and open a FastbootProtocol connection.
         * Returns null if no fastboot device found or connection fails.
         */
        fun open(usbManager: UsbManager): Pair<UsbDevice, FastbootProtocol>? {
            for (device in usbManager.deviceList.values) {
                val proto = openDevice(usbManager, device)
                if (proto != null) return Pair(device, proto)
            }
            return null
        }

        /**
         * Open a specific device as a fastboot connection.
         * Returns null if the device doesn't have a fastboot interface.
         */
        fun openDevice(
            usbManager: UsbManager,
            device: UsbDevice,
        ): FastbootProtocol? {
            var fbInterface: UsbInterface? = null
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (MtkUsbIds.isFastbootInterface(intf.interfaceClass, intf.interfaceSubclass, intf.interfaceProtocol)) {
                    fbInterface = intf
                    break
                }
            }
            if (fbInterface == null) return null

            var epIn: UsbEndpoint? = null
            var epOut: UsbEndpoint? = null
            for (i in 0 until fbInterface.endpointCount) {
                val ep = fbInterface.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) {
                        epIn = ep
                    } else {
                        epOut = ep
                    }
                }
            }
            if (epIn == null || epOut == null) return null

            val conn = usbManager.openDevice(device) ?: return null
            if (!conn.claimInterface(fbInterface, false)) {
                if (!conn.claimInterface(fbInterface, true)) {
                    conn.close()
                    return null
                }
            }

            return FastbootProtocol(conn, fbInterface, epIn, epOut)
        }
    }
}
