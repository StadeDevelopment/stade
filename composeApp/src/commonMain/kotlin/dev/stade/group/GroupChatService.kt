package dev.stade.group

import dev.stade.audio.MIN_VOICE_DURATION_MS
import dev.stade.contact.ContactManager
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.Encoding
import dev.stade.identity.LocalIdentity
import dev.stade.message.encodeImageBody
import dev.stade.message.encodeReactionBody
import dev.stade.message.encodeReplyBody
import dev.stade.message.encodeStickerBody
import dev.stade.message.encodeVideoBody
import dev.stade.message.encodeVoiceBody
import dev.stade.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock

class GroupChatService(
    private val groups: GroupManager,
    private val sync: SyncEngine,
    private val contacts: ContactManager,
    private val crypto: CryptoApi
) {
    private val rosterSent = mutableMapOf<String, Int>()

    fun start(owner: LocalIdentity, scope: CoroutineScope) {
        sync.events.onEach { event ->
            when (event) {
                is SyncEngine.SyncEvent.ContactConnected -> {
                    val contact = contacts.get(event.contactId) ?: return@onEach
                    groups.getPendingJoinForContact(contact.id)?.let { pending ->
                        sendJoinRequest(owner, contact.id, pending)
                    }
                    syncRosterWith(owner, contact.id)
                }
                is SyncEngine.SyncEvent.GroupRosterUpdated -> {
                    val group = groups.getGroup(event.groupId) ?: return@onEach
                    val connected = sync.connectedContacts.value
                    group.memberIds.forEach { memberId ->
                        if (memberId != owner.stadeId && memberId in connected) {
                            syncRosterForGroup(owner, memberId, group)
                        }
                    }
                }
                else -> {}
            }
        }.launchIn(scope)
    }

    suspend fun syncRosterWith(owner: LocalIdentity, contactId: String) {
        val shared = runCatching { groups.groupsForContact(contactId) }.getOrDefault(emptyList())
        shared.forEach { groupId ->
            val group = groups.getGroup(groupId) ?: return@forEach
            syncRosterForGroup(owner, contactId, group)
        }
    }

    private suspend fun syncRosterForGroup(owner: LocalIdentity, contactId: String, group: GroupInfo) {
        val contact = contacts.get(contactId) ?: return
        if (contact.groupProto < GROUP_PROTOCOL_VERSION) return
        val entries = rosterSnapshot(owner, group)
        if (entries.isEmpty()) return
        val body = groups.encodeRoster(group.id, group.name, entries)
        val key = "$contactId|${group.id}"
        val stamp = body.hashCode()
        val unchanged = synchronized(rosterSent) {
            if (rosterSent[key] == stamp) true else {
                rosterSent[key] = stamp
                false
            }
        }
        if (unchanged) return
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        runCatching { sync.queueOutgoing(owner, contact, msgId, body, timestamp) }
    }

    private val contactIdentityLookup: (String) -> GroupMemberEntry? = { memberId ->
        contacts.get(memberId)?.takeIf { it.kind == 0 }?.let {
            GroupMemberEntry(it.id, "", it.publicSigningKey, it.publicMlDsaKey)
        }
    }

    fun rosterSnapshot(owner: LocalIdentity, group: GroupInfo): List<GroupMemberEntry> =
        groups.rosterSnapshot(owner, group.id, contactIdentityLookup)

    fun adoptKnownIdentities(owner: LocalIdentity, groupId: String) {
        groups.adoptIdentities(owner, groupId, contactIdentityLookup)
    }

    private suspend fun fanOut(
        owner: LocalIdentity,
        group: GroupInfo,
        messageId: String,
        timestamp: Long,
        payload: String,
        legacyBody: String?
    ) {
        adoptKnownIdentities(owner, group.id)
        val unreachable = group.memberIds.filter { it != owner.stadeId && contacts.get(it) == null }
        val signed = groups.signFrame(owner, group.id, messageId, timestamp, unreachable, payload)
        group.memberIds.forEach { memberId ->
            if (memberId == owner.stadeId) return@forEach
            val contact = contacts.get(memberId) ?: return@forEach
            val body = if (contact.groupProto >= GROUP_PROTOCOL_VERSION) signed else legacyBody ?: return@forEach
            runCatching { sync.queueOutgoing(owner, contact, messageId, body, timestamp) }
        }
    }

    suspend fun sendMessage(owner: LocalIdentity, groupId: String, body: String, replyToId: String? = null): Boolean {
        val group = groups.getGroup(groupId) ?: return false
        if (group.memberIds.isEmpty()) return false
        val wireBody = if (replyToId != null) encodeReplyBody(replyToId, body) else body
        val messageId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        groups.saveOutgoing(messageId, groupId, owner.stadeId, wireBody, timestamp)
        fanOut(
            owner, group, messageId, timestamp, wireBody,
            legacyBody = "$GRP_MSG_PREFIX$groupId:${owner.stadeId}\n$wireBody"
        )
        return true
    }

    suspend fun sendImage(owner: LocalIdentity, groupId: String, imageBytes: ByteArray, replyToId: String? = null, caption: String = ""): Boolean {
        return sendMessage(owner, groupId, encodeImageBody(imageBytes, caption), replyToId)
    }

    suspend fun sendVoice(owner: LocalIdentity, groupId: String, opusBytes: ByteArray, durationMs: Int, replyToId: String? = null): Boolean {
        if (durationMs < MIN_VOICE_DURATION_MS || opusBytes.isEmpty()) return false
        return sendMessage(owner, groupId, encodeVoiceBody(opusBytes, durationMs), replyToId)
    }

    suspend fun sendVideo(owner: LocalIdentity, groupId: String, videoBytes: ByteArray, replyToId: String? = null, caption: String = ""): Boolean {
        return sendMessage(owner, groupId, encodeVideoBody(videoBytes, caption), replyToId)
    }

    suspend fun sendSticker(owner: LocalIdentity, groupId: String, stickerBytes: ByteArray, replyToId: String? = null): Boolean {
        return sendMessage(owner, groupId, encodeStickerBody(stickerBytes), replyToId)
    }

    suspend fun kickMember(owner: LocalIdentity, group: GroupInfo, memberId: String): Boolean {
        if (group.creatorStadeId.isBlank() || group.creatorStadeId != owner.stadeId) return false
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        fanOut(
            owner, group, msgId, timestamp, "$GRP_ACT_KICK$memberId",
            legacyBody = "$GRP_KICK_PREFIX${group.id}:$memberId"
        )
        groups.removeMember(group.id, memberId)
        return true
    }

    suspend fun leaveGroup(owner: LocalIdentity, group: GroupInfo) {
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        fanOut(
            owner, group, msgId, timestamp, GRP_ACT_LEAVE,
            legacyBody = "$GRP_LEAVE_PREFIX${group.id}"
        )
        groups.leaveGroupLocally(group.id)
    }

    suspend fun sendJoinRequest(owner: LocalIdentity, creatorContactId: String, pending: PendingJoinData) {
        val creator = contacts.get(creatorContactId) ?: return
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val body = "$GRP_JOIN_PREFIX${pending.groupId}:${pending.inviteToken}"
        runCatching {
            sync.queueOutgoing(owner, creator, msgId, body, timestamp)
        }
    }

    suspend fun sendReaction(owner: LocalIdentity, group: GroupInfo, targetMessageId: String, add: Boolean, emoji: String) {
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val inner = encodeReactionBody(targetMessageId, add, emoji)
        fanOut(
            owner, group, msgId, timestamp, "$GRP_ACT_REACTION$inner",
            legacyBody = "$GRP_RXN_PREFIX${group.id}:$inner"
        )
    }

    suspend fun sendGroupInviteToContact(
        owner: LocalIdentity,
        contactId: String,
        inviteCode: String
    ) {
        val contact = contacts.get(contactId) ?: return
        val msgId = Encoding.toHex(crypto.randomBytes(16))
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val body = "$GRP_INV_PREFIX$inviteCode"
        runCatching {
            sync.queueOutgoing(owner, contact, msgId, body, timestamp)
        }
    }

    fun importGroupInvite(owner: LocalIdentity, code: String, onCreatorAlreadyContact: (PendingJoinData, String) -> Unit): GroupInviteData? {
        val data = groups.parseInviteLink(code) ?: return null
        val pending = PendingJoinData(data.groupId, data.groupName, data.inviteToken)
        groups.storePendingJoin(data.creatorStadeId, pending)
        val existingCreator = contacts.findByStadeId(data.creatorStadeId)
        if (existingCreator != null) {
            onCreatorAlreadyContact(pending, existingCreator.id)
        }
        return data
    }
}
