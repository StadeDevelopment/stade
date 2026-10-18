package dev.stade.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ContactPresenceTest {

    @Test
    fun aChosenStatusIsShownEvenWhileTheContactIsConnected() {
        assertEquals(
            ProfileStatus.OFFLINE,
            contactPresence(ProfileStatus.OFFLINE.code, connected = true, everConnected = true),
            "a contact who says they are offline must not also be shown as online"
        )
    }

    @Test
    fun aChosenStatusIsShownWhileTheContactIsDisconnected() {
        assertEquals(
            ProfileStatus.ONLINE,
            contactPresence(ProfileStatus.ONLINE.code, connected = false, everConnected = true)
        )
        assertEquals(
            ProfileStatus.DND,
            contactPresence(ProfileStatus.DND.code, connected = false, everConnected = true)
        )
    }

    @Test
    fun theLiveConnectionFillsInWhenNoStatusWasChosen() {
        assertEquals(ProfileStatus.ONLINE, contactPresence("", connected = true, everConnected = true))
        assertEquals(ProfileStatus.OFFLINE, contactPresence("", connected = false, everConnected = true))
    }

    @Test
    fun anUnknownStatusFallsBackToTheLiveConnection() {
        assertEquals(
            ProfileStatus.ONLINE,
            contactPresence("partying", connected = true, everConnected = true)
        )
        assertEquals(
            ProfileStatus.OFFLINE,
            contactPresence("partying", connected = false, everConnected = true)
        )
    }

    @Test
    fun thereIsNoPresenceBeforeAConnectionHasEverBeenMade() {
        assertEquals(
            ProfileStatus.NONE,
            contactPresence("", connected = false, everConnected = false),
            "a contact we have never reached must not be shown as offline"
        )
        assertEquals(
            ProfileStatus.NONE,
            contactPresence(ProfileStatus.DND.code, connected = false, everConnected = false),
            "a status we could never have received must not be shown"
        )
    }

    @Test
    fun aLiveConnectionIsPresenceEnoughOnItsOwn() {
        assertEquals(
            ProfileStatus.ONLINE,
            contactPresence("", connected = true, everConnected = false)
        )
    }

    @Test
    fun presenceAlwaysResolvesToSomethingShowableOnceConnected() {
        val cases = listOf("", "online", "offline", "idle", "dnd", "nonsense", "ONLINE", " ")
        for (code in cases) {
            for (connected in listOf(true, false)) {
                assertNotEquals(
                    ProfileStatus.NONE,
                    contactPresence(code, connected, everConnected = true),
                    "presence for '$code' must be showable rather than empty"
                )
            }
        }
    }
}
