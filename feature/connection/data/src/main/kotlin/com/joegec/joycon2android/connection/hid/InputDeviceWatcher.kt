package com.joegec.joycon2android.connection.hid

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InputDeviceWatcher(context: Context) : InputManager.InputDeviceListener {

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager

    private val _joycons = MutableStateFlow<List<WatchedJoycon>>(emptyList())
    val joycons: StateFlow<List<WatchedJoycon>> = _joycons.asStateFlow()

    fun start() {
        inputManager.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun refresh() {
        _joycons.value = InputDevice.getDeviceIds().toList().mapNotNull(::watchedJoycon)
    }

    override fun onInputDeviceAdded(deviceId: Int) = refresh()
    override fun onInputDeviceRemoved(deviceId: Int) = refresh()
    override fun onInputDeviceChanged(deviceId: Int) = refresh()

    private fun watchedJoycon(deviceId: Int): WatchedJoycon? {
        val device = InputDevice.getDevice(deviceId) ?: return null
        if (device.sources and InputDevice.SOURCE_GAMEPAD != InputDevice.SOURCE_GAMEPAD) return null
        val model = Switch1Joycon.modelOf(device.vendorId, device.productId) ?: return null
        return WatchedJoycon(device.descriptor, device.name, model)
    }
}
