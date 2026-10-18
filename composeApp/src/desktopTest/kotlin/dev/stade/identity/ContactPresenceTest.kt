package dev.stade.identity

import kotlin.test.Test
import kotlin.test.assertEquals

class ContactPresenceTest {

    @Test
    fun aDisconnectedContactIsOfflineWhateverTheyChose() {
        for (code in listOf("", "online", "idle", "dnd", "offline", "partying")) {
            assertEquals(
                ProfileStatus.OFFLINE,
                contactPresence(code, connected = false, everConnected = true),
                "'$code' must not outlive the connection it was learned over"
            )
        }
    }

    @Test
    fun aChosenStatusIsShownWhileTheContactIsConnected() {
        assertEquals(
            ProfileStatus.DND,
            contactPresence(ProfileStatus.DND.code, connected = true, everConnected = true)
        )
        assertEquals(
            ProfileStatus.IDLE,
            contactPresence(ProfileStatus.IDLE.code, connected = true, everConnected = true)
        )
    }

    @Test
    fun aContactWhoSaysTheyAreOfflineIsTakenAtTheirWord() {
        assertEquals(
            ProfileStatus.OFFLINE,
            contactPresence(ProfileStatus.OFFLINE.code, connected = true, everConnected = true),
            "a contact who says they are offline must not also be shown as online"
        )
    }

    @Test
    fun theLiveConnectionFillsInWhenNoStatusWasChosen() {
        assertEquals(ProfileStatus.ONLINE, contactPresence("", connected = true, everConnected = true))
    }

    @Test
    fun anUnknownStatusFallsBackToBeingConnected() {
        assertEquals(
            ProfileStatus.ONLINE,
            contactPresence("partying", connected = true, everConnected = true)
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
            contactPresence(ProfileStatus.DND.code, connected = false, everConnected = false)
        )
    }

    @Test
    fun aLiveConnectionIsPresenceEnoughOnItsOwn() {
        assertEquals(
            ProfileStatus.ONLINE,
            contactPresence("", connected = true, everConnected = false)
        )
    }
}
