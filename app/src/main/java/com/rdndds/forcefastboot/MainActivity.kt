package com.rdndds.forcefastboot

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rdndds.forcefastboot.theme.ForceFastbootTheme
import com.rdndds.forcefastboot.ui.main.MainScreen
import com.rdndds.forcefastboot.usb.UsbWorkflowController

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val permissionReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == UsbWorkflowController.ACTION_USB_PERMISSION) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    viewModel.usbWorkflow.onPermissionResult(granted, intent.getUsbDevice())
                }
            }
        }

    private val usbReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val device = intent.getUsbDevice() ?: return
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> viewModel.usbWorkflow.onDeviceAttached(device)
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> viewModel.usbWorkflow.onDeviceDetached(device)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen awake while monitoring for bootloop devices
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        registerReceiverCompat(
            usbReceiver,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            },
        )
        registerReceiverCompat(
            permissionReceiver,
            IntentFilter(UsbWorkflowController.ACTION_USB_PERMISSION),
        )

        setContent {
            ForceFastbootTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(
                        logEntries = viewModel.logEntries,
                        onReset = { resetSession() },
                        onCopyLogs = { copyLogsToClipboard() },
                    )
                }
            }
        }

        handleIntent(intent)
        viewModel.usbWorkflow.scanConnectedDevices()
    }

    override fun onResume() {
        super.onResume()
        viewModel.usbWorkflow.scanConnectedDevices()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            intent.getUsbDevice()?.let { viewModel.usbWorkflow.onDeviceAttached(it) }
        }
    }

    private fun resetSession() {
        viewModel.resetSession()
        viewModel.usbWorkflow.scanConnectedDevices()
    }

    private fun copyLogsToClipboard() {
        if (viewModel.logEntries.isEmpty()) {
            Toast.makeText(this, "No logs to copy", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip =
            ClipData.newPlainText(
                "ForceFastboot Logs",
                viewModel.logEntries.joinToString("\n") { it.text },
            )
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        unregisterReceiver(permissionReceiver)
    }
}

/** Typed getParcelableExtra — API 33+ requires the class parameter. */
private fun Intent.getUsbDevice(): UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
} else {
    @Suppress("DEPRECATION")
    getParcelableExtra(UsbManager.EXTRA_DEVICE)
}

/**
 * registerReceiver compat — API 33+ requires RECEIVER_NOT_EXPORTED for
 * explicit-package broadcasts to prevent other apps from intercepting them.
 */
private fun Context.registerReceiverCompat(
    receiver: BroadcastReceiver,
    filter: IntentFilter,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        registerReceiver(receiver, filter)
    }
}
