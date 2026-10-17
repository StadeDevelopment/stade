package dev.stade.stadium

import dev.stade.audio.MIN_VOICE_DURATION_MS
import dev.stade.contact.Contact
import dev.stade.contact.ContactManager
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.Encoding
import dev.stade.group.GroupManager
import dev.stade.identity.LocalIdentity
import dev.stade.message.MessageManager
import dev.stade.message.encodeImageBody
import dev.stade.message.encodeStickerBody
import dev.stade.message.encodeVideoBody
import dev.stade.message.encodeVoiceBody
import dev.stade.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class StadiumChatService(
    private val stadiums: StadiumManager,
    private val sync: SyncEngine,
    private val contacts: ContactManager,
    private val crypto: CryptoApi,
    private val messages: MessageManager? = null,
    private val groupManager: GroupManager? = null
) {
    fun start(owner: LocalIdentity, scope: CoroutineScope) {
        sync.events.onEach { event ->
            when (event) {
                is SyncEngine.SyncEvent.ContactConnected -> {
                    val contact = contacts.get(event.contactId) ?: return@onEach
                    syncSubscriberCounts(owner, contact)
                    val pending = stadiums.getPendingJoinForContact(contact.id) ?: return@onEach
                    if (event.isNew) runCatching { contacts.setKind(contact.id, 1) }
                    sendJoinRequest(owner, contact.id, pending)
                }
                is SyncEngine.SyncEvent.StadiumContactReleased -> {
                    scope.launch {
                        delay(2_000)
                        val fresh = contacts.get(event.contactId)
                        val stillUsed = fresh == null || fresh.kind != 1 ||
                            stadiums.getPendingJoinForContact(event.contactId) != null ||
                            stadiums.allStadiums(owner.id).any { it.creatorStadeId == event.contactId } ||
                            stadiums.stadiumsForContact(event.contactId).isNotEmpty() ||
                            (groupManager?.groupsForContact(event.contactId) ?: emptyList()).isNotEmpty() ||
                            messages?.lastMessage(event.contactId) != null
                        if (!stillUsed) {
                            if (event.forget) sync.forgetContact(event.contactId)
                            else sync.disconnectContact(event.contactId)
                            runCatching { contacts.purge(event.contactId) }
                        }
                    }
                }
                else -> Unit
            }
        }.launchIn(scope)
    }

    private suspend fun syncSubscriberCounts(owner: LocalIdentity, contact: Contact) {
        stadiums.stadiumsForContact(contact.id)
            .mapNotNull { stadiums.getStadium(it) }
            .filter { it.isOwner }
            .forEach { pushSubscriberCount(owner, contact, it) }
        stadiums.allStadiums(owner.id)
            .filter { !it.isOwner && it.creatorStadeId == contact.id }
            .forEach { requestSubscriberCount(owner, it) }
    }

    private suspend fun pushSubscriberCount(owner: LocalIdentity, contact: Contact, stadium: StadiumInfo) {
        val body = "$STD_COUNT_PREFIX${stadium.id}:${stadiums.syncOwnMemberCount(stadium.id)}"
        runCatching {
            sync.queueOutgoing(
                owner, contact,
                Encoding.toHex(crypto.randomBytes(16)),
                body,
                Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    suspend fun requestSubscriberCount(owner: LocalIdentity, stadium: StadiumInfo) {
        if (stadium.isOwner) return
        val creator = contacts.get(stadium.creatorStadeId) ?: return
        val body = "$STD_COUNT_REQUEST_PREFIX${stadium.id}"
        runCatching {
            sync.queueOutgoing(
                owner, creator,
                Encoding.toHex(crypto.randomBytes(16)),
                body,
                Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    suspend fun post(owner: LocalIdentity, stadium: StadiumInfo, body: String): Boolean {
        if (!stadium.isOwner) return false
        val members = stadiums.getMemberContactIds(stadium.id)
        val messageId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val encodedBody = "$STD_MSG_PREFIX${stadium.id}:${members.size}\n$body"
        stadiums.saveOutgoing(messageId, stadium.id, body, timestamp)
        members.forEach { contactId ->
            val contact = contacts.get(contactId) ?: return@forEach
            runCatching {
                sync.queueOutgoing(owner, contact, messageId, encodedBody, timestamp)
            }
        }
        return true
    }

    suspend fun postImage(owner: LocalIdentity, stadium: StadiumInfo, imageBytes: ByteArray, caption: String = ""): Boolean =
        post(owner, stadium, encodeImageBody(imageBytes, caption))

    suspend fun postVoice(owner: LocalIdentity, stadium: StadiumInfo, opusBytes: ByteArray, durationMs: Int): Boolean {
        if (durationMs < MIN_VOICE_DURATION_MS || opusBytes.isEmpty()) return false
        return post(owner, stadium, encodeVoiceBody(opusBytes, durationMs))
    }

    suspend fun postVideo(owner: LocalIdentity, stadium: StadiumInfo, videoBytes: ByteArray, caption: String = ""): Boolean =
        post(owner, stadium, encodeVideoBody(videoBytes, caption))

    suspend fun postSticker(owner: LocalIdentity, stadium: StadiumInfo, stickerBytes: ByteArray): Boolean =
        post(owner, stadium, encodeStickerBody(stickerBytes))

    suspend fun deleteAsOwner(owner: LocalIdentity, stadium: StadiumInfo) {
        if (!stadium.isOwner) return
        val members = stadiums.getMemberContactIds(stadium.id)
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val body = "$STD_DELETE_PREFIX${stadium.id}"
        for (contactId in members) {
            val contact = contacts.get(contactId) ?: continue
            val queued = runCatching { sync.queueOutgoing(owner, contact, msgId, body, timestamp) }.isSuccess
            if (queued && contact.kind == 1) {
                stadiums.markPendingFarewell(contactId, msgId, forget = false)
            }
        }
        stadiums.deleteStadium(stadium.id)
    }

    suspend fun deleteMessageAsOwner(owner: LocalIdentity, stadium: StadiumInfo, messageId: String) {
        if (!stadium.isOwner) return
        val members = stadiums.getMemberContactIds(stadium.id)
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val body = "$STD_MSG_DELETE_PREFIX${stadium.id}:$messageId"
        members.forEach { contactId ->
            val contact = contacts.get(contactId) ?: return@forEach
            runCatching { sync.queueOutgoing(owner, contact, msgId, body, timestamp) }
        }
        stadiums.deleteMessage(messageId)
    }

    suspend fun leave(owner: LocalIdentity, stadium: StadiumInfo): Boolean {
        if (stadium.isOwner) return false
        val ownerContact = contacts.get(stadium.creatorStadeId)
        if (ownerContact != null) {
            val msgId = Encoding.toHex(crypto.randomBytes(16))
            val timestamp = Clock.System.now().toEpochMilliseconds()
            val body = "$STD_LEAVE_PREFIX${stadium.id}"
            val queued = runCatching { sync.queueOutgoing(owner, ownerContact, msgId, body, timestamp) }.isSuccess
            stadiums.leaveStadium(stadium.id)
            if (queued && ownerContact.kind == 1 &&
                stadiums.allStadiums(owner.id).none { it.creatorStadeId == ownerContact.id }
            ) {
                stadiums.markPendingFarewell(ownerContact.id, msgId)
            }
        } else {
            stadiums.leaveStadium(stadium.id)
        }
        return true
    }

    suspend fun sendStadiumInviteToContact(owner: LocalIdentity, contactId: String, inviteCode: String) {
        val contact = contacts.get(contactId) ?: return
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val body = "$STD_INV_PREFIX$inviteCode"
        runCatching {
            sync.queueOutgoing(owner, contact, msgId, body, timestamp)
        }
    }

    suspend fun sendJoinRequest(owner: LocalIdentity, creatorContactId: String, pending: PendingStadiumJoin) {
        val creator = contacts.get(creatorContactId) ?: return
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val body = "$STD_JOIN_PREFIX${pending.stadiumId}:${pending.inviteToken}"
        runCatching {
            sync.queueOutgoing(owner, creator, msgId, body, timestamp)
        }
    }
}
