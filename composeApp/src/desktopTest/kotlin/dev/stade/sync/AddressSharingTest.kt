package dev.stade.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val own = listOf(
    "tor://abcdefghijklmnop.onion:5901",
    "lan://192.168.1.24:5901",
    "lan://10.0.0.7:5901"
)

class AddressSharingTest {

    @Test
    fun aTorPeerIsNeverToldOurLocalNetworkAddresses() {
        val shared = shareableAddresses(own, "tor://somepeer.onion:5901")

        assertEquals(listOf("tor://abcdefghijklmnop.onion:5901"), shared)
        assertTrue(
            shared.none { it.startsWith(LAN_ADDRESS_PREFIX) },
            "talking over Tor must not disclose which local network we are on"
        )
    }

    @Test
    fun aPeerOnTheSameNetworkStillLearnsOurLocalAddresses() {
        val shared = shareableAddresses(own, "lan://192.168.1.9:5901")

        assertEquals(own, shared, "a LAN peer still needs the LAN addresses to reconnect")
    }

    @Test
    fun anUnknownTransportIsTreatedAsRemote() {
        val shared = shareableAddresses(own, "")

        assertTrue(
            shared.none { it.startsWith(LAN_ADDRESS_PREFIX) },
            "an unrecognised peer address must get the cautious answer"
        )
    }

    @Test
    fun blankEntriesAreNeverShared() {
        assertEquals(emptyList(), shareableAddresses(listOf("", "   "), "lan://192.168.1.9:5901").filter { it.isBlank() })
    }
}
