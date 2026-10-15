package com.joegec.joycon2android.connection.evdev

data class EvdevEvent(val type: Int, val code: Int, val value: Int)
