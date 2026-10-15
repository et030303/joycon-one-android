package com.joegec.joycon2android.connection.evdev

import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_DPAD_DOWN
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_DPAD_LEFT
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_DPAD_RIGHT
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_DPAD_UP
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_EAST
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_MODE
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_NORTH
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_SELECT
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_SOUTH
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_START
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_THUMBL
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_THUMBR
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_TL
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_TL2
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_TR
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_TR2
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_WEST
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_Z
import com.joegec.joycon2android.model.JoyconButton
import com.joegec.joycon2android.model.Side

/** hid-nintendo's codes, measured: docs/switch1.md */
object JoyconEvdevButtons {

    private val left = mapOf(
        BTN_TL2 to JoyconButton.ZL, BTN_TL to JoyconButton.L,
        BTN_TR to JoyconButton.SlLeft, BTN_TR2 to JoyconButton.SrLeft,
        BTN_SELECT to JoyconButton.Minus, BTN_Z to JoyconButton.Capture, BTN_THUMBL to JoyconButton.LS,
        BTN_DPAD_UP to JoyconButton.Up, BTN_DPAD_DOWN to JoyconButton.Down,
        BTN_DPAD_LEFT to JoyconButton.Left, BTN_DPAD_RIGHT to JoyconButton.Right,
    )

    private val right = mapOf(
        BTN_TR2 to JoyconButton.ZR, BTN_TR to JoyconButton.R,
        BTN_TL to JoyconButton.SlRight, BTN_TL2 to JoyconButton.SrRight,
        BTN_START to JoyconButton.Plus, BTN_MODE to JoyconButton.Home, BTN_THUMBR to JoyconButton.RS,
        BTN_EAST to JoyconButton.A, BTN_SOUTH to JoyconButton.B,
        BTN_NORTH to JoyconButton.X, BTN_WEST to JoyconButton.Y,
    )

    fun forSide(side: Side): Map<Int, JoyconButton> = when (side) {
        Side.LEFT -> left
        Side.RIGHT -> right
        Side.PRO, Side.UNKNOWN -> emptyMap()
    }
}
