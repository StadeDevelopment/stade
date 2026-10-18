package dev.stade.ui

import dev.stade.contact.Contact
import dev.stade.group.GroupInfo
import dev.stade.stadium.StadiumInfo
import dev.stade.ui.screens.ChatListItem
import dev.stade.ui.screens.SelectionKind
import dev.stade.ui.screens.chatSelectionSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun contactItem(id: String, muted: Boolean = false) = ChatListItem.ContactItem(
    Contact(
        id = id,
        ownerId = "owner",
        nickname = id,
        publicSigningKey = ByteArray(0),
        publicHandshakeKey = ByteArray(0),
        publicMlKemKey = ByteArray(0),
        publicMlDsaKey = ByteArray(0),
        rootKey = ByteArray(0),
        ratchetState = null,
        isAlice = false,
        verified = false,
        lastSeen = 0L,
        createdAt = 0L,
        muted = muted
    )
)

private fun groupItem(id: String, muted: Boolean = false) = ChatListItem.GroupItem(
    GroupInfo(id = id, ownerId = "owner", name = id, inviteToken = "t", createdAt = 0L, muted = muted)
)

private fun stadiumItem(id: String, muted: Boolean = false) = ChatListItem.StadiumItem(
    StadiumInfo(
        id = id,
        ownerId = "owner",
        name = id,
        creatorStadeId = "creator",
        isOwner = false,
        inviteToken = "t",
        memberCount = 0L,
        createdAt = 0L,
        muted = muted
    )
)

class ChatSelectionTest {

    @Test
    fun aMixedSelectionOffersNoDestructiveAction() {
        val mixed = listOf(
            listOf(groupItem("g1"), stadiumItem("s1")),
            listOf(groupItem("g1"), contactItem("c1")),
            listOf(stadiumItem("s1"), contactItem("c1")),
            listOf(groupItem("g1"), stadiumItem("s1"), contactItem("c1"))
        )

        mixed.forEach { items ->
            assertNull(
                chatSelectionSummary(items, emptySet()).kind,
                "a selection of mixed chat types must not offer leaving or deleting"
            )
        }
    }

    @Test
    fun eachPureSelectionReportsItsOwnKind() {
        assertEquals(
            SelectionKind.Contacts,
            chatSelectionSummary(listOf(contactItem("c1"), contactItem("c2")), emptySet()).kind
        )
        assertEquals(
            SelectionKind.Groups,
            chatSelectionSummary(listOf(groupItem("g1"), groupItem("g2")), emptySet()).kind
        )
        assertEquals(
            SelectionKind.Stadiums,
            chatSelectionSummary(listOf(stadiumItem("s1"), stadiumItem("s2")), emptySet()).kind
        )
    }

    @Test
    fun anEmptySelectionOffersNothing() {
        val summary = chatSelectionSummary(emptyList(), setOf("grp_g1"))

        assertEquals(0, summary.count)
        assertNull(summary.kind)
        assertFalse(summary.allArchived)
        assertFalse(summary.allMuted)
    }

    @Test
    fun archivedIsOnlyReportedWhenEveryChatIsArchived() {
        val items = listOf(groupItem("g1"), contactItem("c1"))

        assertTrue(chatSelectionSummary(items, setOf("grp_g1", "c1")).allArchived)
        assertFalse(chatSelectionSummary(items, setOf("grp_g1")).allArchived)
    }

    @Test
    fun mutedIsOnlyReportedWhenEveryChatIsMuted() {
        assertTrue(
            chatSelectionSummary(
                listOf(groupItem("g1", muted = true), stadiumItem("s1", muted = true)),
                emptySet()
            ).allMuted
        )
        assertFalse(
            chatSelectionSummary(
                listOf(groupItem("g1", muted = true), stadiumItem("s1")),
                emptySet()
            ).allMuted
        )
    }
}
