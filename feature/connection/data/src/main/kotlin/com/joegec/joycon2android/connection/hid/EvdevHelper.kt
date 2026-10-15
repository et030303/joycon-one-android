package com.joegec.joycon2android.connection.hid

import android.content.Context
import com.joegec.joycon2android.connection.evdev.EvdevStateDecoder
import com.joegec.joycon2android.model.Side

/** The native `evdev_reader`, copied where the shell user can run it. */
class EvdevHelper(private val context: Context, private val launcher: PrivilegedLauncher) {

    private val deployed: Boolean by lazy { deploy() }

    fun listNodes(): List<EvdevNode> {
        if (!deployed) return emptyList()
        val process = launcher.start(arrayOf(REMOTE_PATH, "list")) ?: return emptyList()
        return try {
            EvdevNodeListing.parse(process.inputStream.bufferedReader().readText())
        } finally {
            process.destroy()
        }
    }

    fun open(node: EvdevNode, side: Side): EvdevSession? {
        if (!deployed) return null
        val process = launcher.start(arrayOf(REMOTE_PATH, "read", node.path)) ?: return null
        return EvdevSession.open(process, EvdevStateDecoder(side))
    }

    private fun deploy(): Boolean {
        val local = context.applicationInfo.nativeLibraryDir + "/libevdev_reader.so"
        val copy = launcher.start(arrayOf("sh", "-c", "cp $local $REMOTE_PATH && chmod 755 $REMOTE_PATH")) ?: return false
        copy.waitFor()
        return true
    }

    private companion object {
        const val REMOTE_PATH = "/data/local/tmp/.evdev_reader"
    }
}
