package com.joegec.joycon2android.connection.hid

/** Asks for privileged access if it isn't granted yet; null means refused or unavailable. */
fun interface PrivilegedLauncherProvider {
    fun acquire(onResult: (PrivilegedLauncher?) -> Unit)
}
