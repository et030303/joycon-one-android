package com.joegec.joycon2android.connection.hid

import android.content.Context
import com.joegec.joycon2android.connection.ControllerRepository
import com.joegec.joycon2android.connection.data.R
import com.joegec.joycon2android.model.ConnectedJoycon
import com.joegec.joycon2android.model.ControllerModel
import com.joegec.joycon2android.model.JoyconConnectionState
import com.joegec.joycon2android.model.JoyconInput
import com.joegec.joycon2android.model.PlayerNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** Joy-Cons paired in Android's Bluetooth settings; they connect and disconnect there, not here. */
class HidControllerSource(
    private val context: Context,
    scope: CoroutineScope,
    private val watcher: InputDeviceWatcher,
    private val launcherProvider: PrivilegedLauncherProvider,
) : ControllerRepository {

    private val inputs = MutableStateFlow<Map<String, JoyconInput>>(emptyMap())
    private val retries = MutableStateFlow(0)
    private val sessions = ConcurrentHashMap<String, EvdevSession>()
    private val grabbed = ConcurrentHashMap.newKeySet<String>()

    private var helper: EvdevHelper? = null
    private var accessRefused = false

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()
    override val scanning: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    override val controllers: StateFlow<List<ConnectedJoycon>> =
        combine(watcher.joycons, inputs) { joycons, live -> joycons.map { it.toConnectedJoycon(live) } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch(Dispatchers.IO) {
            combine(watcher.joycons, retries) { joycons, _ -> joycons }.collect(::sync)
        }
    }

    override fun startScan() {
        accessRefused = false
        watcher.refresh()
        retries.value++
    }

    override fun setPlayerLed(address: String, player: PlayerNumber?) {
        val grab = player != null
        if (grab) grabbed += address else grabbed -= address
        sessions[address]?.setGrabbed(grab)
    }

    override fun stopScan() = Unit
    override fun disconnect(address: String) = Unit
    override fun disconnectAll() = Unit
    override fun setControllerType(address: String, model: ControllerModel?) = Unit
    override fun emitError(message: String) = Unit

    private suspend fun sync(joycons: List<WatchedJoycon>) {
        val present = joycons.map { Switch1Joycon.addressOf(it.descriptor) }.toSet()
        (sessions.keys - present).forEach(::drop)

        val waiting = joycons.filter { !sessions.containsKey(Switch1Joycon.addressOf(it.descriptor)) }
        if (waiting.isEmpty()) return
        val helper = helperOrNull() ?: return
        val nodes = helper.listNodes()
        waiting.forEach { open(helper, nodes, it) }
    }

    private fun open(helper: EvdevHelper, nodes: List<EvdevNode>, joycon: WatchedJoycon) {
        val address = Switch1Joycon.addressOf(joycon.descriptor)
        val node = EvdevNodeListing.padFor(nodes, joycon.descriptor) ?: return
        val session = helper.open(node, joycon.model.defaultSide) ?: return
        session.onInput = { input -> inputs.value += address to input }
        session.onClosed = { if (sessions.remove(address, session)) inputs.value -= address }
        sessions[address] = session
        inputs.value += address to JoyconInput()
        if (address in grabbed) session.setGrabbed(true)
    }

    private fun drop(address: String) {
        sessions.remove(address)?.close()
        inputs.value -= address
    }

    private suspend fun helperOrNull(): EvdevHelper? {
        helper?.let { return it }
        if (accessRefused) return null
        val launcher = suspendCancellableCoroutine { continuation -> launcherProvider.acquire(continuation::resume) }
        if (launcher == null) {
            accessRefused = true
            _error.value = context.getString(R.string.error_switch1_needs_shizuku)
            return null
        }
        _error.value = null
        return EvdevHelper(context, launcher).also { helper = it }
    }

    private fun WatchedJoycon.toConnectedJoycon(live: Map<String, JoyconInput>): ConnectedJoycon {
        val address = Switch1Joycon.addressOf(descriptor)
        val input = live[address]
        return ConnectedJoycon(
            address = address,
            side = model.defaultSide,
            deviceName = name,
            model = model,
            connectionState = JoyconConnectionState(connected = true, ready = input != null, deviceName = name),
            input = input ?: JoyconInput(),
            ready = input != null,
        )
    }
}
