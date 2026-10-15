package com.joegec.joycon2android.connection.evdev

import org.junit.Assert.assertEquals
import org.junit.Test

class EvdevRecordTest {

    @Test
    fun `a key press decodes`() {
        val record = byteArrayOf(0x01, 0x00, 0x36, 0x01, 0x01, 0x00, 0x00, 0x00)
        assertEquals(EvdevEvent(EvdevCodes.EV_KEY, EvdevCodes.BTN_TL, 1), EvdevRecord.decode(record))
    }

    @Test
    fun `a negative axis value keeps its sign`() {
        val record = byteArrayOf(0x03, 0x00, 0x01, 0x00, 0x01, 0x80.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertEquals(-32767, EvdevRecord.decode(record).value)
    }
}
