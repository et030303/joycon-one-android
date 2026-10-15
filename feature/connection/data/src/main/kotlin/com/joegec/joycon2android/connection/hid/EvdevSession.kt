package com.joegec.joycon2android.connection.hid

import android.util.Log
import com.joegec.joycon2android.connection.evdev.EvdevRecord
import com.joegec.joycon2android.connection.evdev.EvdevStateDecoder
import com.joegec.joycon2android.model.JoyconInput
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

/** One `evdev_reader read` process: decoded input out, grab commands in. */
class EvdevSession private constructor(
    private val process: HelperProcess,
    private val decoder: EvdevStateDecoder,
) {

    @Volatile
    var onInput: (JoyconInput) -> Unit = {}

    @Volatile
    var onClosed: () -> Unit = {}

    @Volatile
    private var closed = false

    fun setGrabbed(grabbed: Boolean) = send(if (grabbed) GRAB else RELEASE)

    fun close() {
        closed = true
        runCatching { process.outputStream.close() }
        process.destroy()
    }

    private fun send(command: Int) {
        try {
            process.outputStream.write(command)
            process.outputStream.flush()
        } catch (e: IOException) {
            Log.w(TAG, "Helper no longer accepts commands", e)
        }
    }

    private fun startReading() {
        Thread({ readRecords() }, "evdev-session").apply { isDaemon = true }.start()
    }

    private fun readRecords() {
        val input = DataInputStream(process.inputStream.buffered())
        val record = ByteArray(EvdevRecord.SIZE)
        try {
            while (true) {
                input.readFully(record)
                decoder.apply(EvdevRecord.decode(record))?.let(onInput)
            }
        } catch (_: IOException) {
        } finally {
            if (!closed) Log.w(TAG, "Helper ended")
            close()
            onClosed()
        }
    }

    companion object {
        private const val TAG = "EvdevSession"
        private const val GRAB = 'G'.code
        private const val RELEASE = 'U'.code
        private const val READY = "OK"
        private const val NEWLINE = 0x0A

        fun open(process: HelperProcess, decoder: EvdevStateDecoder): EvdevSession? {
            val greeting = readGreeting(process.inputStream)
            if (greeting != READY) {
                Log.e(TAG, "Helper did not start: $greeting")
                process.destroy()
                return null
            }
            return EvdevSession(process, decoder).also { it.startReading() }
        }

        // Unbuffered: a buffer could swallow the first event records behind the greeting.
        private fun readGreeting(stream: InputStream): String? = try {
            val line = StringBuilder()
            while (true) {
                val next = stream.read()
                if (next < 0 || next == NEWLINE) break
                line.append(next.toChar())
            }
            line.toString().trim()
        } catch (_: IOException) {
            null
        }
    }
}
