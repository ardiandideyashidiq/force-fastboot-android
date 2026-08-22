package com.rdndds.forcefastboot.usb

import android.hardware.usb.UsbDeviceConnection
import com.hoho.android.usbserial.driver.UsbSerialPort

/**
 * Holds a CDC ACM serial port together with its backing USB connection so both
 * are closed exactly once and quietly.
 */
class SerialPortSession(
    val connection: UsbDeviceConnection,
    val port: UsbSerialPort,
) : AutoCloseable {
    override fun close() {
        try {
            port.close()
        } catch (_: Exception) {
        }
        try {
            connection.close()
        } catch (_: Exception) {
        }
    }
}
