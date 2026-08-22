package com.rdndds.forcefastboot

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class LogEntryTest {
    @Test
    fun resolveLogColor_successReturnsGreen() {
        val color = resolveLogColor("2026-08-22 [SUCCESS] Device is now in FASTBOOT mode!")
        assertEquals(Color(0xFF66BB6A), color)
    }

    @Test
    fun resolveLogColor_actionReturnsCyan() {
        val color = resolveLogColor("2026-08-22 [ACTION] Starting FASTBOOT burst loop...")
        assertEquals(Color(0xFF29B6F6), color)
    }

    @Test
    fun resolveLogColor_burstReturnsCyan() {
        val color = resolveLogColor("2026-08-22 [BURST] Sending FASTBOOT to PID 0x2000")
        assertEquals(Color(0xFF29B6F6), color)
    }

    @Test
    fun resolveLogColor_sentReturnsCyan() {
        val color = resolveLogColor("2026-08-22 [SENT] FASTBOOT command sent -> response: READY")
        assertEquals(Color(0xFF29B6F6), color)
    }

    @Test
    fun resolveLogColor_getvarReturnsAmber() {
        val color = resolveLogColor("2026-08-22 [GETVAR] Querying: current-slot")
        assertEquals(Color(0xFFFFB74D), color)
    }

    @Test
    fun resolveLogColor_attachedReturnsPurple() {
        val color = resolveLogColor("2026-08-22 [ATTACHED] MTK PRELOADER")
        assertEquals(Color(0xFFBA68C8), color)
    }

    @Test
    fun resolveLogColor_detachedReturnsSlate() {
        val color = resolveLogColor("2026-08-22 [DETACHED] FASTBOOT")
        assertEquals(Color(0xFF90A4AE), color)
    }

    @Test
    fun resolveLogColor_failedReturnsRed() {
        val color = resolveLogColor("2026-08-22 [FAILED] Could not send FASTBOOT command")
        assertEquals(Color(0xFFEF5350), color)
    }

    @Test
    fun resolveLogColor_errorReturnsRed() {
        val color = resolveLogColor("2026-08-22 [ERROR] USB device disconnected")
        assertEquals(Color(0xFFEF5350), color)
    }

    @Test
    fun resolveLogColor_timeoutReturnsRed() {
        val color = resolveLogColor("2026-08-22 [TIMEOUT] Device did not respond")
        assertEquals(Color(0xFFEF5350), color)
    }

    @Test
    fun resolveLogColor_crossMarkReturnsRed() {
        val color = resolveLogColor("2026-08-22 ✗ FAIL: device not found")
        assertEquals(Color(0xFFEF5350), color)
    }

    @Test
    fun resolveLogColor_infoReturnsGray() {
        val color = resolveLogColor("2026-08-22 [INFO] Permission cached, sending FASTBOOT burst...")
        assertEquals(Color(0xFFB0BEC5), color)
    }

    @Test
    fun resolveLogColor_respReturnsTeal() {
        val color = resolveLogColor("2026-08-22 [RESP] Raw data received")
        assertEquals(Color(0xFF80CBC4), color)
    }

    @Test
    fun resolveLogColor_unknownReturnsDefaultLight() {
        val color = resolveLogColor("2026-08-22 Some random log message")
        assertEquals(Color(0xFFECEFF1), color)
    }

    @Test
    fun logEntry_monotonicIds() {
        val entry1 = LogEntry(id = 0, text = "first", color = Color.White)
        val entry2 = LogEntry(id = 1, text = "second", color = Color.White)
        val entry3 = LogEntry(id = 2, text = "third", color = Color.White)
        assert(entry1.id < entry2.id)
        assert(entry2.id < entry3.id)
    }

    @Test
    fun logEntry_equalityWithSameFields() {
        val a = LogEntry(id = 42, text = "hello", color = Color.Red)
        val b = LogEntry(id = 42, text = "hello", color = Color.Red)
        assertEquals(a, b)
    }

    @Test
    fun logEntry_inequalityWithDifferentText() {
        val a = LogEntry(id = 42, text = "hello", color = Color.Red)
        val b = LogEntry(id = 42, text = "world", color = Color.Red)
        assert(a != b)
    }
}
