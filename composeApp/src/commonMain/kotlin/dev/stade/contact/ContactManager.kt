package dev.stade.contact

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.stade.crypto.CryptoApi
import dev.stade.db.StadeDb
import dev.stade.identity.LocalIdentity
import dev.stade.identity.StadeId
import dev.stade.notification.ShortcutEntityKind
import dev.stade.notification.removeConversationShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

const val PROMOTE_TO_CONTACT_PREFIX = "CTUP:"

private const val FORGOTTEN_KV_KEY = "sync.forgotten"

class ContactManager(private val db: StadeDb, private val crypto: CryptoApi) {

    fun observeContacts(ownerId: String): Flow<List<Contact>> =
        db.stadeDbQueries.selectContacts(ownerId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun observeContact(contactId: String): Flow<Contact?> =
        db.stadeDbQueries.selectContact(contactId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }

    fun all(): List<Contact> =
        db.stadeDbQueries.selectAllContacts().executeAsList().map { it.toDomain() }

    fun contacts(ownerId: String): List<Contact> =
        db.stadeDbQueries.selectContacts(ownerId).executeAsList().map { it.toDomain() }

    fun get(id: String): Contact? =
        db.stadeDbQueries.selectContact(id).executeAsOneOrNull()?.toDomain()

    fun findByPublicKey(key: ByteArray): Contact? =
        db.stadeDbQueries.selectContactByPublicKey(key).executeAsOneOrNull()?.toDomain()

    fun findByStadeId(stadeId: String): Contact? =
        db.stadeDbQueries.selectContactByStadeId(stadeId).executeAsOneOrNull()?.toDomain()

    suspend fun addFromHandshake(
        owner: LocalIdentity,
        nickname: String,
        peerSigningKey: ByteArray,
        peerHandshakeKey: ByteArray,
        peerMlKemKey: ByteArray,
        peerMlDsaKey: ByteArray,
        rootKey: ByteArray,
        isAlice: Boolean,
        addresses: List<String> = emptyList(),
        kind: Int = 0
    ): Contact = withContext(Dispatchers.Default) {
        val id = StadeId.derive(peerSigningKey, peerMlDsaKey, crypto::hash)
        val now = Clock.System.now().toEpochMilliseconds()
        val addrJoined = addresses.filter { it.isNotBlank() }.joinToString("\n")
        db.stadeDbQueries.insertContact(
            id, owner.id, nickname, peerSigningKey, peerHandshakeKey,
            peerMlKemKey, peerMlDsaKey,
            rootKey, null, if (isAlice) 1 else 0, 0, 0L, now, addrJoined, kind.toLong(), null
        )
        Contact(
            id = id,
            ownerId = owner.id,
            nickname = nickname,
            publicSigningKey = peerSigningKey,
            publicHandshakeKey = peerHandshakeKey,
            publicMlKemKey = peerMlKemKey,
            publicMlDsaKey = peerMlDsaKey,
            rootKey = rootKey,
            ratchetState = null,
            isAlice = isAlice,
            verified = false,
            lastSeen = 0L,
            createdAt = now,
            addresses = addrJoined.split("\n").filter { it.isNotBlank() },
            kind = kind
        )
    }

    fun setAddresses(contactId: String, addresses: List<String>) {
        val joined = addresses.filter { it.isNotBlank() }.distinct().joinToString("\n")
        db.stadeDbQueries.setContactAddresses(joined, contactId)
    }

    fun saveRatchet(contactId: String, snapshot: ByteArray) {
        db.stadeDbQueries.setContactRatchet(snapshot, contactId)
    }

    fun markSeen(contactId: String, timestamp: Long) {
        db.stadeDbQueries.setContactSeen(timestamp, contactId)
    }

    fun rename(contactId: String, nickname: String) {
        db.stadeDbQueries.renameContact(nickname, contactId)
    }

    fun setProfile(contactId: String, bio: String, status: String) {
        db.stadeDbQueries.setContactProfile(bio, status, contactId)
    }

    fun verify(contactId: String) {
        db.stadeDbQueries.setContactVerified(contactId)
    }

    fun forgottenIds(): Set<String> =
        db.stadeDbQueries.getKv(FORGOTTEN_KV_KEY).executeAsOneOrNull()
            ?.decodeToString()
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

    fun setForgottenIds(ids: Set<String>) {
        if (ids.isEmpty()) {
            db.stadeDbQueries.deleteKv(FORGOTTEN_KV_KEY)
        } else {
            db.stadeDbQueries.putKv(FORGOTTEN_KV_KEY, ids.joinToString("\n").encodeToByteArray())
        }
    }

    fun setKind(contactId: String, kind: Int) {
        db.stadeDbQueries.setContactKind(kind.toLong(), contactId)
    }

    fun setMuted(contactId: String, muted: Boolean) {
        db.stadeDbQueries.setContactMuted(if (muted) 1L else 0L, contactId)
    }

    fun setAvatar(contactId: String, avatar: ByteArray?) {
        db.stadeDbQueries.setContactAvatar(avatar, contactId)
    }

    fun setGroupProto(contactId: String, version: Int) {
        db.stadeDbQueries.setContactGroupProto(version.toLong(), contactId)
    }

    fun delete(contactId: String) {
        db.stadeDbQueries.deleteContact(contactId)
        removeConversationShortcut(ShortcutEntityKind.CONTACT, contactId)
    }

    fun purge(contactId: String) {
        db.stadeDbQueries.transaction {
            db.stadeDbQueries.deleteOutboxForContact(contactId)
            db.stadeDbQueries.deleteMessagesForContact(contactId)
            db.stadeDbQueries.deleteScheduledForContact(contactId)
            db.stadeDbQueries.deleteContact(contactId)
        }
        removeConversationShortcut(ShortcutEntityKind.CONTACT, contactId)
    }

    fun removeFromView(contactId: String) {
        db.stadeDbQueries.transaction {
            db.stadeDbQueries.deleteOutboxForContact(contactId)
            db.stadeDbQueries.deleteMessagesForContact(contactId)
            db.stadeDbQueries.deleteScheduledForContact(contactId)
            db.stadeDbQueries.setContactKind(2L, contactId)
        }
        removeConversationShortcut(ShortcutEntityKind.CONTACT, contactId)
    }

    private fun dev.stade.db.Contact.toDomain(): Contact =
        Contact(
            id = id,
            ownerId = ownerId,
            nickname = nickname,
            publicSigningKey = publicKey,
            publicHandshakeKey = handshakePublicKey,
            publicMlKemKey = mlkemPublicKey,
            publicMlDsaKey = mldsaPublicKey,
            rootKey = rootKey,
            ratchetState = ratchetState,
            isAlice = isAlice == 1L,
            verified = verified == 1L,
            lastSeen = lastSeen,
            createdAt = createdAt,
            addresses = addresses.split("\n").filter { it.isNotBlank() },
            kind = kind.toInt(),
            muted = muted == 1L,
            avatar = avatar,
            bio = bio,
            status = status,
            groupProto = groupProto.toInt()
        )
}
