package com.joegec.joycon2android.connection.evdev

import kotlin.math.roundToInt

/** hid-nintendo's ±32767 onto 0..4095 centred on 2048, as StickCalibrator outputs it. */
object EvdevStickScale {
    private const val EVDEV_MAX = 32767
    private const val CENTRE = 2048
    private const val HALF_SPAN = 2047

    fun toStick(value: Int): Int =
        (CENTRE + value.coerceIn(-EVDEV_MAX, EVDEV_MAX) * HALF_SPAN.toDouble() / EVDEV_MAX).roundToInt()

    /** Evdev has up negative; the app has up high. */
    fun toStickInverted(value: Int): Int = toStick(-value)
}
