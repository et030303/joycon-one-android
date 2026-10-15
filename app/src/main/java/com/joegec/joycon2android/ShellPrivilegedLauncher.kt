package com.joegec.joycon2android

import com.joegec.joycon2android.connection.hid.HelperProcess
import com.joegec.joycon2android.connection.hid.PrivilegedLauncher
import com.joegec.joycon2android.gamepad.privileged.PrivilegedShell
import com.joegec.joycon2android.gamepad.privileged.ShellProcess
import java.io.InputStream
import java.io.OutputStream

/** Lets the connection feature start shell processes without depending on the gamepad feature. */
class ShellPrivilegedLauncher(private val shell: PrivilegedShell) : PrivilegedLauncher {

    override fun start(argv: Array<String>): HelperProcess? = shell.newProcess(argv)?.let(::ShellHelperProcess)

    private class ShellHelperProcess(private val process: ShellProcess) : HelperProcess {
        override val inputStream: InputStream get() = process.inputStream
        override val outputStream: OutputStream get() = process.outputStream
        override fun waitFor() = process.waitFor()
        override fun destroy() = process.destroy()
    }
}
