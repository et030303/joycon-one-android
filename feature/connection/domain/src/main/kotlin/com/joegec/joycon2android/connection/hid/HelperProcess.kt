package com.joegec.joycon2android.connection.hid

import java.io.InputStream
import java.io.OutputStream

interface HelperProcess {
    val inputStream: InputStream
    val outputStream: OutputStream
    fun waitFor()
    fun destroy()
}
