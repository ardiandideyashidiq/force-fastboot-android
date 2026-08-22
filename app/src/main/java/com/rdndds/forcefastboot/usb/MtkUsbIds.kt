package com.rdndds.forcefastboot.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice

/**
 * MediaTek USB vendor/product IDs and interface constants.
 *
 * MTK preloader mode uses CDC ACM serial (VID 0x0E8D, PID 0x2000/0x0003).
 * After receiving the FASTBOOT command, the device re-enumerates as a
 * fastboot device (PID 0x201C/0x2024) with class 0xFF (vendor-specific),
 * subclass 0x42, protocol 0x03.
 */
object MtkUsbIds {
    const val VID = 0x0E8D

    val PRELOADER_PIDS = setOf(0x0003, 0x2000)

    const val FASTBOOT_SUBCLASS = 0x42
    const val FASTBOOT_PROTOCOL = 0x03

    fun isPreloaderPid(pid: Int): Boolean = pid in PRELOADER_PIDS

    fun isFastbootInterface(
        interfaceClass: Int,
        subclass: Int,
        protocol: Int,
    ): Boolean = interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
        subclass == FASTBOOT_SUBCLASS &&
        protocol == FASTBOOT_PROTOCOL
}

fun UsbDevice.hasFastbootInterface(): Boolean {
    for (i in 0 until interfaceCount) {
        val intf = getInterface(i)
        if (MtkUsbIds.isFastbootInterface(intf.interfaceClass, intf.interfaceSubclass, intf.interfaceProtocol)) {
            return true
        }
    }
    return false
}

fun UsbDevice.isPreloader(): Boolean = vendorId == MtkUsbIds.VID && MtkUsbIds.isPreloaderPid(productId)

fun UsbDevice.isMtkFastboot(): Boolean = vendorId == MtkUsbIds.VID && hasFastbootInterface()
