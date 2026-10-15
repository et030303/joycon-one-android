package com.joegec.joycon2android.connection.hid

import com.joegec.joycon2android.model.ControllerModel

data class WatchedJoycon(val descriptor: String, val name: String, val model: ControllerModel)
