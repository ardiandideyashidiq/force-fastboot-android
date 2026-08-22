package com.rdndds.forcefastboot

import com.rdndds.forcefastboot.usb.MtkUsbIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MtkUsbIdsTest {
    @Test
    fun testVendorId() {
        assertEquals(3725, MtkUsbIds.VID)
    }

    @Test
    fun testPreloaderPidsAccepted() {
        assertTrue(MtkUsbIds.isPreloaderPid(0x0003))
        assertTrue(MtkUsbIds.isPreloaderPid(0x2000))
        assertEquals(setOf(0x0003, 0x2000), MtkUsbIds.PRELOADER_PIDS)
    }

    @Test
    fun testOtherPidsRejected() {
        assertFalse(MtkUsbIds.isPreloaderPid(0x201C))
        assertFalse(MtkUsbIds.isPreloaderPid(0x2024))
        assertFalse(MtkUsbIds.isPreloaderPid(0x0001))
        assertFalse(MtkUsbIds.isPreloaderPid(0))
    }

    @Test
    fun testFastbootInterfaceMatch() {
        // class 255 (vendor spec), subclass 0x42, protocol 0x03
        assertTrue(MtkUsbIds.isFastbootInterface(255, 0x42, 0x03))
    }

    @Test
    fun testFastbootInterfaceMismatch() {
        assertFalse(MtkUsbIds.isFastbootInterface(255, 0x42, 0x01))
        assertFalse(MtkUsbIds.isFastbootInterface(255, 0x01, 0x03))
        assertFalse(MtkUsbIds.isFastbootInterface(8, 0x42, 0x03))
        assertFalse(MtkUsbIds.isFastbootInterface(0, 0, 0))
    }
}
