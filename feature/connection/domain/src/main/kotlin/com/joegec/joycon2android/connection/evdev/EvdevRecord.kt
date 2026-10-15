package com.joegec.joycon2android.connection.evdev

/** What `evdev_reader read` writes: u16 type, u16 code, i32 value, little-endian. */
object EvdevRecord {
    const val SIZE = 8

    fun decode(bytes: ByteArray) = EvdevEvent(
        type = u16(bytes, 0),
        code = u16(bytes, 2),
        value = (bytes[4].toInt() and 0xFF) or
            ((bytes[5].toInt() and 0xFF) shl 8) or
            ((bytes[6].toInt() and 0xFF) shl 16) or
            (bytes[7].toInt() shl 24),
    )

    private fun u16(bytes: ByteArray, at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
}
