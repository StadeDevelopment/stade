package dev.stade.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchivedNotificationsTest {

    @Test
    fun anArchivedChatStaysSilent() {
        assertTrue(
            archivedChatStaysSilent(setOf("c1"), "c1", autoUnarchive = false),
            "a chat put away must not ring"
        )
    }

    @Test
    fun aChatThatIsNotArchivedIsAnnounced() {
        assertFalse(archivedChatStaysSilent(setOf("c2"), "c1", autoUnarchive = false))
        assertFalse(archivedChatStaysSilent(emptySet(), "c1", autoUnarchive = false))
    }

    @Test
    fun anArchivedChatThatIsAboutToLeaveTheArchiveIsAnnounced() {
        assertFalse(
            archivedChatStaysSilent(setOf("c1"), "c1", autoUnarchive = true),
            "a message unarchives the chat, so silencing it would hide a live conversation"
        )
    }

    @Test
    fun groupsAndStadiumsAreMatchedByTheirOwnKeys() {
        val archived = setOf(GROUP_CHAT_KEY_PREFIX + "g1", STADIUM_CHAT_KEY_PREFIX + "s1")
        assertTrue(archivedChatStaysSilent(archived, GROUP_CHAT_KEY_PREFIX + "g1", false))
        assertTrue(archivedChatStaysSilent(archived, STADIUM_CHAT_KEY_PREFIX + "s1", false))
        assertFalse(
            archivedChatStaysSilent(archived, "g1", false),
            "a contact sharing an id with an archived group must still be announced"
        )
    }
}
