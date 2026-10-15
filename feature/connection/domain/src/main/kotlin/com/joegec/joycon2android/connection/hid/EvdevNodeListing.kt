package com.joegec.joycon2android.connection.hid

/** Output of `evdev_reader list`: path, vendor, product, uniq and name, tab-separated. */
object EvdevNodeListing {

    fun parse(text: String): List<EvdevNode> = text.lineSequence().mapNotNull(::parseLine).toList()

    fun padFor(nodes: List<EvdevNode>, descriptor: String): EvdevNode? = nodes.firstOrNull {
        !it.isImu && Switch1Joycon.descriptorOf(it.vendorId, it.productId, it.uniq) == descriptor
    }

    private fun parseLine(line: String): EvdevNode? {
        val fields = line.trimEnd('\r').split('\t')
        if (fields.size != FIELD_COUNT) return null
        val vendor = fields[1].toIntOrNull(HEX) ?: return null
        val product = fields[2].toIntOrNull(HEX) ?: return null
        return EvdevNode(fields[0], vendor, product, fields[3], fields[4])
    }

    private const val FIELD_COUNT = 5
    private const val HEX = 16
}
