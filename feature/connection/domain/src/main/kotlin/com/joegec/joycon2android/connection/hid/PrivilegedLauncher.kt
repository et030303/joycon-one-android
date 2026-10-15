package com.joegec.joycon2android.connection.hid

/** Starts shell-uid processes. */
interface PrivilegedLauncher {
    fun start(argv: Array<String>): HelperProcess?
}
