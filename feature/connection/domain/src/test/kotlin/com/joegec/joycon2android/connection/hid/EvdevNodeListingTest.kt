package com.joegec.joycon2android.connection.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvdevNodeListingTest {

    private val listing = listOf(
        "/dev/input/event6\t057e\t2006\t98:E8:FA:88:BE:2B\tNintendo Switch Left Joy-Con IMU",
        "/dev/input/event5\t057e\t2006\t98:E8:FA:88:BE:2B\tNintendo Switch Left Joy-Con",
        "/dev/input/event4\t0000\t0000\t\tchipone-tddi",
    ).joinToString("\r\n")

    private val leftDescriptor = "0a811fc45b91caac5a7fa79a9ff3777aaf784021"

    @Test
    fun `every line becomes a node`() {
        assertEquals(3, EvdevNodeListing.parse(listing).size)
    }

    @Test
    fun `fields are read as hex ids`() {
        val node = EvdevNodeListing.parse(listing)[1]
        assertEquals(0x057E, node.vendorId)
        assertEquals(0x2006, node.productId)
    }

    @Test
    fun `a line with missing fields is skipped`() {
        assertEquals(emptyList<EvdevNode>(), EvdevNodeListing.parse("OK\n/dev/input/event1\t0001"))
    }

    @Test
    fun `the pad is found by descriptor, not the imu that shares its address`() {
        assertEquals("/dev/input/event5", EvdevNodeListing.padFor(EvdevNodeListing.parse(listing), leftDescriptor)?.path)
    }

    @Test
    fun `an unknown descriptor finds nothing`() {
        assertNull(EvdevNodeListing.padFor(EvdevNodeListing.parse(listing), "ffff"))
    }
}
