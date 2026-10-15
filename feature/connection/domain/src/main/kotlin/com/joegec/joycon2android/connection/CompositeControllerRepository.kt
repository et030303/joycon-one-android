package com.joegec.joycon2android.connection

import com.joegec.joycon2android.model.ConnectedJoycon
import com.joegec.joycon2android.model.ControllerModel
import com.joegec.joycon2android.model.PlayerNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Per-controller calls go to the source that lists the address. */
class CompositeControllerRepository(
    scope: CoroutineScope,
    private val sources: List<ControllerRepository>,
) : ControllerRepository {

    override val controllers: StateFlow<List<ConnectedJoycon>> =
        combine(sources.map { it.controllers }) { lists -> lists.flatMap { it } }
            .stateIn(scope, SharingStarted.Eagerly, sources.flatMap { it.controllers.value })

    override val scanning: StateFlow<Boolean> =
        combine(sources.map { it.scanning }) { states -> states.any { it } }
            .stateIn(scope, SharingStarted.Eagerly, sources.any { it.scanning.value })

    override val error: StateFlow<String?> =
        combine(sources.map { it.error }) { errors -> errors.firstOrNull { it != null } }
            .stateIn(scope, SharingStarted.Eagerly, sources.firstNotNullOfOrNull { it.error.value })

    override fun startScan() = sources.forEach { it.startScan() }

    override fun stopScan() = sources.forEach { it.stopScan() }

    override fun disconnectAll() = sources.forEach { it.disconnectAll() }

    override fun disconnect(address: String) = owner(address)?.disconnect(address) ?: Unit

    override fun setPlayerLed(address: String, player: PlayerNumber?) =
        owner(address)?.setPlayerLed(address, player) ?: Unit

    override fun setControllerType(address: String, model: ControllerModel?) =
        (owner(address) ?: sources.first()).setControllerType(address, model)

    override fun emitError(message: String) = sources.first().emitError(message)

    private fun owner(address: String): ControllerRepository? =
        sources.firstOrNull { source -> source.controllers.value.any { it.address == address } }
}
