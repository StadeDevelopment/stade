package dev.stade.sync

const val LAN_ADDRESS_PREFIX = "lan://"

fun shareableAddresses(own: List<String>, peerAddress: String): List<String> {
    val peerIsLan = peerAddress.startsWith(LAN_ADDRESS_PREFIX)
    return own.filter { address ->
        address.isNotBlank() && (peerIsLan || !address.startsWith(LAN_ADDRESS_PREFIX))
    }
}
