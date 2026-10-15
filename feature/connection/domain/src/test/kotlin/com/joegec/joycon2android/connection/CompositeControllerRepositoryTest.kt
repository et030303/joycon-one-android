package com.joegec.joycon2android.connection

import com.joegec.joycon2android.model.ConnectedJoycon
import com.joegec.joycon2android.model.ControllerModel
import com.joegec.joycon2android.model.PlayerNumber
import com.joegec.joycon2android.model.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class CompositeControllerRepositoryTest {

    private class FakeRepository(addresses: List<String>) : ControllerRepository {
        override val controllers = MutableStateFlow(addresses.map(::joycon))
        override val scanning = MutableStateFlow(false)
        override val error: MutableStateFlow<String?> = MutableStateFlow(null)
        val disconnected = mutableListOf<String>()
        val types = mutableListOf<String>()
        var scans = 0

        override fun startScan() { scans++ }
        override fun stopScan() = Unit
        override fun disconnect(address: String) { disconnected += address }
        override fun disconnectAll() = Unit
        override fun setPlayerLed(address: String, player: PlayerNumber?) = Unit
        override fun setControllerType(address: String, model: ControllerModel?) { types += address }
        override fun emitError(message: String) { error.value = message }
    }

    private val ble = FakeRepository(listOf("AA:BB"))
    private val hid = FakeRepository(listOf("hid:one"))
    private val composite = CompositeControllerRepository(
        CoroutineScope(Dispatchers.Unconfined),
        listOf(ble, hid),
    )

    @Test
    fun `controllers from every source are listed together`() {
        assertEquals(listOf("AA:BB", "hid:one"), composite.controllers.value.map { it.address })
    }

    @Test
    fun `a controller added to one source appears`() {
        hid.controllers.value = listOf(joycon("hid:one"), joycon("hid:two"))
        assertEquals(3, composite.controllers.value.size)
    }

    @Test
    fun `disconnect goes only to the source that lists the address`() {
        composite.disconnect("hid:one")
        assertEquals(emptyList<String>(), ble.disconnected)
        assertEquals(listOf("hid:one"), hid.disconnected)
    }

    @Test
    fun `scanning starts every source`() {
        composite.startScan()
        assertEquals(1, ble.scans)
        assertEquals(1, hid.scans)
    }

    @Test
    fun `scanning reports true while any source scans`() {
        ble.scanning.value = true
        assertEquals(true, composite.scanning.value)
    }

    @Test
    fun `a type choice for an address no source lists still reaches the first source`() {
        composite.setControllerType("11:22", null)
        assertEquals(listOf("11:22"), ble.types)
    }

    @Test
    fun `an error from any source is reported`() {
        hid.error.value = "boom"
        assertEquals("boom", composite.error.value)
    }
}

private fun joycon(address: String) = ConnectedJoycon(address = address, side = Side.LEFT, deviceName = address)
