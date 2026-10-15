package com.joegec.joycon2android.connection.evdev

import com.joegec.joycon2android.connection.evdev.EvdevCodes.ABS_RX
import com.joegec.joycon2android.connection.evdev.EvdevCodes.ABS_RY
import com.joegec.joycon2android.connection.evdev.EvdevCodes.ABS_X
import com.joegec.joycon2android.connection.evdev.EvdevCodes.ABS_Y
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_DPAD_UP
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_EAST
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_TL
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_TR
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_TR2
import com.joegec.joycon2android.model.JoyconButton
import com.joegec.joycon2android.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvdevStateDecoderTest {

    private fun key(code: Int, down: Boolean) = EvdevEvent(EvdevCodes.EV_KEY, code, if (down) 1 else 0)
    private fun axis(code: Int, value: Int) = EvdevEvent(EvdevCodes.EV_ABS, code, value)
    private val report = EvdevEvent(EvdevCodes.EV_SYN, EvdevCodes.SYN_REPORT, 0)

    private fun EvdevStateDecoder.feed(vararg events: EvdevEvent) = events.map { apply(it) }.last()

    @Test
    fun `nothing is emitted until the report ends`() {
        assertNull(EvdevStateDecoder(Side.LEFT).apply(key(BTN_TL, true)))
    }

    @Test
    fun `a left joy-con's shoulder codes are L and SL`() {
        val input = EvdevStateDecoder(Side.LEFT).feed(key(BTN_TL, true), key(BTN_TR, true), report)!!
        assertEquals(setOf(JoyconButton.L.id, JoyconButton.SlLeft.id), input.pressed)
    }

    @Test
    fun `the same codes mean R and ZR on a right joy-con`() {
        val input = EvdevStateDecoder(Side.RIGHT).feed(key(BTN_TR, true), key(BTN_TR2, true), report)!!
        assertEquals(setOf(JoyconButton.R.id, JoyconButton.ZR.id), input.pressed)
    }

    @Test
    fun `a released button leaves the set`() {
        val decoder = EvdevStateDecoder(Side.RIGHT)
        decoder.feed(key(BTN_EAST, true), report)
        assertEquals(emptySet<String>(), decoder.feed(key(BTN_EAST, false), report)!!.pressed)
    }

    @Test
    fun `the left d-pad buttons become directions`() {
        val input = EvdevStateDecoder(Side.LEFT).feed(key(BTN_DPAD_UP, true), report)!!
        assertEquals(setOf(JoyconButton.Up.id), input.pressed)
    }

    @Test
    fun `a code the side does not have is ignored`() {
        assertEquals(emptySet<String>(), EvdevStateDecoder(Side.LEFT).feed(key(BTN_EAST, true), report)!!.pressed)
    }

    @Test
    fun `an idle stick is centred`() {
        val input = EvdevStateDecoder(Side.LEFT).feed(report)!!
        assertEquals(2048, input.stickX)
        assertEquals(2048, input.stickY)
    }

    @Test
    fun `pushing a stick up raises Y and right raises X`() {
        val input = EvdevStateDecoder(Side.LEFT).feed(axis(ABS_X, 32767), axis(ABS_Y, -32767), report)!!
        assertEquals(4095, input.stickX)
        assertEquals(4095, input.stickY)
    }

    @Test
    fun `a stick pushed fully down and left reaches the bottom of the range`() {
        val input = EvdevStateDecoder(Side.LEFT).feed(axis(ABS_X, -32767), axis(ABS_Y, 32767), report)!!
        assertEquals(1, input.stickX)
        assertEquals(1, input.stickY)
    }

    @Test
    fun `a right joy-con's stick arrives on the primary stick`() {
        val input = EvdevStateDecoder(Side.RIGHT).feed(axis(ABS_RX, 32767), axis(ABS_RY, 0), report)!!
        assertEquals(4095, input.stickX)
        assertEquals(2048, input.stickY)
        assertEquals(2048, input.rightStickX)
    }

    @Test
    fun `a left joy-con ignores the right stick axes`() {
        assertEquals(2048, EvdevStateDecoder(Side.LEFT).feed(axis(ABS_RX, 32767), report)!!.stickX)
    }
}
