package com.joegec.joycon2android.connection.hid

import com.joegec.joycon2android.model.ControllerModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Switch1JoyconTest {

    @Test
    fun `nintendo product 2006 is a left joy-con`() {
        assertEquals(ControllerModel.JOYCON_LEFT, Switch1Joycon.modelOf(0x057E, 0x2006))
    }

    @Test
    fun `nintendo product 2007 is a right joy-con`() {
        assertEquals(ControllerModel.JOYCON_RIGHT, Switch1Joycon.modelOf(0x057E, 0x2007))
    }

    @Test
    fun `another vendor with a joy-con product id is not one`() {
        assertNull(Switch1Joycon.modelOf(0x054C, 0x2006))
    }

    @Test
    fun `a switch 1 pro controller is not a joy-con`() {
        assertNull(Switch1Joycon.modelOf(0x057E, 0x2009))
    }

    @Test
    fun `the descriptor matches what android reported for a real left joy-con`() {
        assertEquals(
            "0a811fc45b91caac5a7fa79a9ff3777aaf784021",
            Switch1Joycon.descriptorOf(0x057E, 0x2006, "98:E8:FA:88:BE:2B"),
        )
    }
}
