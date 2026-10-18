package dev.stade.contact

import dev.stade.identity.LocalIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

private fun contact(
    id: String = "c1",
    nickname: String = "B",
    status: String = "",
    bio: String = "",
    verified: Boolean = false,
    muted: Boolean = false,
    addresses: List<String> = emptyList(),
    avatar: ByteArray? = null,
    ratchetState: ByteArray? = null,
    lastSeen: Long = 0L
) = Contact(
    id = id,
    ownerId = "owner",
    nickname = nickname,
    publicSigningKey = ByteArray(0),
    publicHandshakeKey = ByteArray(0),
    publicMlKemKey = ByteArray(0),
    publicMlDsaKey = ByteArray(0),
    rootKey = ByteArray(0),
    ratchetState = ratchetState,
    isAlice = false,
    verified = verified,
    lastSeen = lastSeen,
    createdAt = 0L,
    addresses = addresses,
    muted = muted,
    avatar = avatar,
    bio = bio,
    status = status
)

private fun identity(nickname: String = "A", status: String = "", bio: String = "") = LocalIdentity(
    id = "me",
    nickname = nickname,
    publicSigningKey = ByteArray(0),
    privateSigningKey = ByteArray(0),
    publicHandshakeKey = ByteArray(0),
    privateHandshakeKey = ByteArray(0),
    publicMlKemKey = ByteArray(0),
    privateMlKemKey = ByteArray(0),
    publicMlDsaKey = ByteArray(0),
    privateMlDsaKey = ByteArray(0),
    createdAt = 0L,
    bio = bio,
    status = status
)

class ContactChangeVisibilityTest {

    @Test
    fun aChosenStatusIsAChange() {
        assertNotEquals(contact(status = ""), contact(status = "dnd"))
    }

    @Test
    fun clearingAChosenStatusIsAChange() {
        assertNotEquals(contact(status = "dnd"), contact(status = ""))
    }

    @Test
    fun aStatusChangeSurvivesListComparison() {
        val before = listOf(contact(id = "c1", status = "dnd"), contact(id = "c2"))
        val after = listOf(contact(id = "c1", status = ""), contact(id = "c2"))
        assertNotEquals(before, after, "a list whose only change is a status must not look unchanged")
    }

    @Test
    fun theOtherProfileFieldsAreChangesToo() {
        assertNotEquals(contact(), contact(nickname = "B2"))
        assertNotEquals(contact(), contact(bio = "hello"))
        assertNotEquals(contact(), contact(verified = true))
        assertNotEquals(contact(), contact(muted = true))
        assertNotEquals(contact(), contact(addresses = listOf("onion")))
        assertNotEquals(contact(), contact(avatar = ByteArray(1)))
    }

    @Test
    fun sessionTrafficIsNotAChange() {
        assertEquals(
            contact(ratchetState = ByteArray(1) { 1 }, lastSeen = 1L),
            contact(ratchetState = ByteArray(1) { 2 }, lastSeen = 2L),
            "ratchet and last-seen writes are invisible, so they must not look like profile changes"
        )
    }

    @Test
    fun myOwnProfileChangesAreChanges() {
        assertNotEquals(identity(status = ""), identity(status = "dnd"))
        assertNotEquals(identity(nickname = "A"), identity(nickname = "A2"))
        assertNotEquals(identity(bio = ""), identity(bio = "hi"))
    }
}
