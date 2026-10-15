package com.joegec.joycon2android.connection.hid

data class EvdevNode(
    val path: String,
    val vendorId: Int,
    val productId: Int,
    val uniq: String,
    val name: String,
) {
    val isImu: Boolean get() = name.endsWith("IMU")
}
