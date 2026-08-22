package com.rdndds.forcefastboot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastbootProtocolTest {
    @Test
    fun testParseOkayWithPayload() {
        val raw = "OKAY_a".toByteArray(Charsets.US_ASCII)
        val resp = FastbootProtocol.parseResponseBuffer(raw, raw.size)
        assertTrue(resp is FastbootProtocol.Response.Okay)
        assertEquals("_a", (resp as FastbootProtocol.Response.Okay).data)
    }

    @Test
    fun testParseOkayEmptyPayload() {
        val raw = "OKAY".toByteArray(Charsets.US_ASCII)
        val resp = FastbootProtocol.parseResponseBuffer(raw, raw.size)
        assertTrue(resp is FastbootProtocol.Response.Okay)
        assertEquals("", (resp as FastbootProtocol.Response.Okay).data)
    }

    @Test
    fun testParseFail() {
        val raw = "FAILDevice is locked".toByteArray(Charsets.US_ASCII)
        val resp = FastbootProtocol.parseResponseBuffer(raw, raw.size)
        assertTrue(resp is FastbootProtocol.Response.Fail)
        assertEquals("Device is locked", (resp as FastbootProtocol.Response.Fail).message)
    }

    @Test
    fun testParseInfo() {
        val raw = "INFO(bootloader) slot-count: 2".toByteArray(Charsets.US_ASCII)
        val resp = FastbootProtocol.parseResponseBuffer(raw, raw.size)
        assertTrue(resp is FastbootProtocol.Response.Info)
        assertEquals("(bootloader) slot-count: 2", (resp as FastbootProtocol.Response.Info).message)
    }

    @Test
    fun testParseShortRead() {
        val raw = "OK".toByteArray(Charsets.US_ASCII)
        val resp = FastbootProtocol.parseResponseBuffer(raw, raw.size)
        assertTrue(resp is FastbootProtocol.Response.Fail)
        assertTrue((resp as FastbootProtocol.Response.Fail).message.contains("Short read"))
    }

    @Test
    fun testParseUnknownStatus() {
        val raw = "WHATsomething".toByteArray(Charsets.US_ASCII)
        val resp = FastbootProtocol.parseResponseBuffer(raw, raw.size)
        assertTrue(resp is FastbootProtocol.Response.Fail)
        assertTrue((resp as FastbootProtocol.Response.Fail).message.contains("Unknown status"))
    }
}
