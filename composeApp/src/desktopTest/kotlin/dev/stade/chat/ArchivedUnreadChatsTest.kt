package dev.stade.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ArchivedUnreadChatsTest {

    @Test
    fun nothingArchivedCountsForNothing() {
        assertEquals(
            0,
            archivedUnreadChats(emptySet(), setOf("c1", "c2"), setOf("g1"))
        )
    }

    @Test
    fun onlyArchivedChatsWithUnreadMessagesCount() {
        assertEquals(
            1,
            archivedUnreadChats(setOf("c1", "c2"), setOf("c1"), emptySet())
        )
    }

    @Test
    fun eachChatCountsOnceHoweverManyMessagesItHolds() {
        assertEquals(
            2,
            archivedUnreadChats(
                setOf("c1", "c2", "c3"),
                setOf("c1", "c2"),
                emptySet()
            ),
            "the badge counts chats, not messages"
        )
    }

    @Test
    fun archivedGroupsCountAlongsideContacts() {
        assertEquals(
            2,
            archivedUnreadChats(setOf("c1", "grp_g1"), setOf("c1"), setOf("g1"))
        )
    }

    @Test
    fun anUnreadChatThatIsNotArchivedIsLeftToTheChatList() {
        assertEquals(
            0,
            archivedUnreadChats(setOf("c1"), setOf("c2"), setOf("g1"))
        )
    }

    @Test
    fun readingAnArchivedChatDropsItFromTheCount() {
        val archived = setOf("c1", "grp_g1")
        assertEquals(2, archivedUnreadChats(archived, setOf("c1"), setOf("g1")))
        assertEquals(1, archivedUnreadChats(archived, emptySet(), setOf("g1")))
        assertEquals(0, archivedUnreadChats(archived, emptySet(), emptySet()))
    }

    @Test
    fun stadiumsAreNotCounted() {
        assertEquals(
            0,
            archivedUnreadChats(setOf("std_s1"), setOf("s1"), setOf("s1")),
            "stadiums are broadcasts, so they do not contribute to the archive badge"
        )
    }
}
