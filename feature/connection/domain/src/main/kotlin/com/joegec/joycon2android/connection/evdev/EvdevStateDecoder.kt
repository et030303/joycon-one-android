package com.joegec.joycon2android.connection.evdev

import com.joegec.joycon2android.model.JoyconInput
import com.joegec.joycon2android.model.MotionSupport
import com.joegec.joycon2android.model.Side

/** One Joy-Con's event stream; [apply] returns the input on each report and null between. */
class EvdevStateDecoder(side: Side) {

    private val buttonCodes = JoyconEvdevButtons.forSide(side)
    private val stickXCode = if (side == Side.RIGHT) EvdevCodes.ABS_RX else EvdevCodes.ABS_X
    private val stickYCode = if (side == Side.RIGHT) EvdevCodes.ABS_RY else EvdevCodes.ABS_Y

    private val pressed = linkedSetOf<String>()
    private var stickX = EvdevStickScale.toStick(0)
    private var stickY = EvdevStickScale.toStickInverted(0)

    fun apply(event: EvdevEvent): JoyconInput? {
        when (event.type) {
            EvdevCodes.EV_KEY -> applyKey(event)
            EvdevCodes.EV_ABS -> applyAxis(event)
            EvdevCodes.EV_SYN -> if (event.code == EvdevCodes.SYN_REPORT) return snapshot()
        }
        return null
    }

    private fun applyKey(event: EvdevEvent) {
        val button = buttonCodes[event.code] ?: return
        if (event.value != 0) pressed += button.id else pressed -= button.id
    }

    private fun applyAxis(event: EvdevEvent) {
        when (event.code) {
            stickXCode -> stickX = EvdevStickScale.toStick(event.value)
            stickYCode -> stickY = EvdevStickScale.toStickInverted(event.value)
        }
    }

    private fun snapshot() = JoyconInput(
        pressed = pressed.toSet(),
        stickX = stickX,
        stickY = stickY,
        motionSupport = MotionSupport.None,
    )
}
