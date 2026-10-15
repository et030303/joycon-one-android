package com.joegec.joycon2android.connection.hid

import com.joegec.joycon2android.model.ControllerModel
import java.security.MessageDigest

object Switch1Joycon {
    const val ADDRESS_PREFIX = "hid:"

    private const val NINTENDO_VENDOR_ID = 0x057E
    private const val LEFT_PRODUCT_ID = 0x2006
    private const val RIGHT_PRODUCT_ID = 0x2007

    fun modelOf(vendorId: Int, productId: Int): ControllerModel? = when {
        vendorId != NINTENDO_VENDOR_ID -> null
        productId == LEFT_PRODUCT_ID -> ControllerModel.JOYCON_LEFT
        productId == RIGHT_PRODUCT_ID -> ControllerModel.JOYCON_RIGHT
        else -> null
    }

    fun addressOf(descriptor: String) = ADDRESS_PREFIX + descriptor

    /** How Android's EventHub names an input device that has a unique id. */
    fun descriptorOf(vendorId: Int, productId: Int, uniq: String): String {
        val raw = ":%04x:%04x:uniqueId:%s".format(vendorId, productId, uniq)
        return MessageDigest.getInstance("SHA-1").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
