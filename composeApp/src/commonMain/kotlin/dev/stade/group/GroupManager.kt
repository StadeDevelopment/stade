package dev.stade.group

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.PqCrypto
import dev.stade.crypto.Encoding
import dev.stade.chat.chatWallpaperKey
import dev.stade.db.StadeDb
import dev.stade.identity.LocalIdentity
import dev.stade.identity.StadeId
import dev.stade.identity.sanitizeDisplayName
import dev.stade.message.SearchFilter
import dev.stade.message.SearchResult
import dev.stade.message.previewBody
import dev.stade.notification.ShortcutEntityKind
import dev.stade.notification.removeConversationShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val MAX_ROSTER_FIELD_LEN = 120

private fun sanitizeRosterField(value: String): String =
    sanitizeDisplayName(
        value.asSequence().filter { it != GRP_ROSTER_FIELD_SEP }.joinToString(""),
        MAX_ROSTER_FIELD_LEN
    )

class GroupManager(
    private val db: StadeDb,
    private val crypto: CryptoApi,
    private val pq: PqCrypto
) {

    private val pendingJoins = mutableMapOf<String, PendingJoinData>()

    fun createGroup(ownerId: String, creatorStadeId: String, name: String): GroupInfo {
        val id = Encoding.toHex(crypto.randomBytes(16))
        val inviteToken = Encoding.toHex(crypto.randomBytes(16))
        val now = Clock.System.now().toEpochMilliseconds()
        db.stadeDbQueries.insertGroup(id, ownerId, name, inviteToken, now, creatorStadeId)
        return GroupInfo(id, ownerId, name, inviteToken, now, emptyList(), creatorStadeId)
    }

    fun getGroup(groupId: String): GroupInfo? =
        db.stadeDbQueries.selectGroup(groupId).executeAsOneOrNull()?.let {
            val members = db.stadeDbQueries.selectGroupMembers(it.id).executeAsList().map { m -> m.contactId }
            GroupInfo(it.id, it.ownerId, it.name, it.inviteToken, it.createdAt, members, it.creatorStadeId, muted = it.muted == 1L)
        }

    fun allGroups(ownerId: String): List<GroupInfo> {
        val rows = db.stadeDbQueries.selectGroups(ownerId).executeAsList()
        if (rows.isEmpty()) return emptyList()
        val membersByGroup = db.stadeDbQueries.selectAllGroupMembersForOwner(ownerId)
            .executeAsList()
            .groupBy({ it.groupId }, { it.contactId })
        return rows.map { row ->
            GroupInfo(row.id, row.ownerId, row.name, row.inviteToken, row.createdAt, membersByGroup[row.id].orEmpty(), row.creatorStadeId, muted = row.muted == 1L)
        }
    }

    fun observeGroups(ownerId: String): Flow<List<GroupInfo>> =
        db.stadeDbQueries.selectGroups(ownerId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                if (rows.isEmpty()) return@map emptyList()
                val membersByGroup = db.stadeDbQueries.selectAllGroupMembersForOwner(ownerId)
                    .executeAsList()
                    .groupBy({ it.groupId }, { it.contactId })
                rows.map { row ->
                    GroupInfo(row.id, row.ownerId, row.name, row.inviteToken, row.createdAt, membersByGroup[row.id].orEmpty(), row.creatorStadeId, muted = row.muted == 1L)
                }
            }

    fun setMuted(groupId: String, muted: Boolean) {
        db.stadeDbQueries.setGroupMuted(if (muted) 1L else 0L, groupId)
    }

    fun addMember(groupId: String, contactId: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.stadeDbQueries.insertGroupMember(groupId, contactId, now)
    }

    fun getMembers(groupId: String): List<String> =
        db.stadeDbQueries.selectGroupMembers(groupId).executeAsList().map { it.contactId }

    fun isMember(groupId: String, memberId: String): Boolean =
        db.stadeDbQueries.isGroupMember(groupId, memberId).executeAsOne() > 0L

    fun roster(groupId: String): List<GroupMemberEntry> =
        db.stadeDbQueries.selectGroupMembers(groupId).executeAsList().map {
            GroupMemberEntry(it.contactId, it.nickname, it.signingKey, it.mldsaKey)
        }

    fun observeRoster(groupId: String): Flow<List<GroupMemberEntry>> =
        db.stadeDbQueries.selectGroupMembers(groupId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { GroupMemberEntry(it.contactId, it.nickname, it.signingKey, it.mldsaKey) } }

    fun memberIdentity(groupId: String, memberId: String): GroupMemberEntry? =
        db.stadeDbQueries.selectGroupMemberIdentity(groupId, memberId).executeAsOneOrNull()?.let {
            GroupMemberEntry(it.contactId, it.nickname, it.signingKey, it.mldsaKey)
        }

    fun setMemberIdentity(groupId: String, entry: GroupMemberEntry): Boolean {
        val signing = entry.signingKey ?: return false
        val mldsa = entry.mldsaKey ?: return false
        if (StadeId.derive(signing, mldsa, crypto::hash) != entry.memberId) return false
        val known = memberIdentity(groupId, entry.memberId)
        val knownSigning = known?.signingKey
        val knownMldsa = known?.mldsaKey
        if (knownSigning != null && knownMldsa != null &&
            (!knownSigning.contentEquals(signing) || !knownMldsa.contentEquals(mldsa))
        ) {
            return false
        }
        addMember(groupId, entry.memberId)
        val announced = sanitizeRosterField(entry.nickname)
        val nickname = announced.ifBlank { known?.nickname.orEmpty() }
        db.stadeDbQueries.setGroupMemberIdentity(nickname, signing, mldsa, groupId, entry.memberId)
        return true
    }

    fun selfRosterEntry(owner: LocalIdentity): GroupMemberEntry =
        GroupMemberEntry(owner.stadeId, owner.nickname, owner.publicSigningKey, owner.publicMlDsaKey)

    fun rosterSnapshot(
        owner: LocalIdentity,
        groupId: String,
        lookup: (String) -> GroupMemberEntry?
    ): List<GroupMemberEntry> {
        val stored = roster(groupId).associateBy { it.memberId }
        return getMembers(groupId).mapNotNull { memberId ->
            when {
                memberId == owner.stadeId -> selfRosterEntry(owner)
                stored[memberId]?.hasIdentity == true -> stored[memberId]
                else -> lookup(memberId)
            }
        }.filter { it.hasIdentity }
    }

    fun adoptIdentities(owner: LocalIdentity, groupId: String, lookup: (String) -> GroupMemberEntry?) {
        rosterSnapshot(owner, groupId, lookup).forEach { entry ->
            if (memberIdentity(groupId, entry.memberId)?.hasIdentity != true) {
                runCatching { setMemberIdentity(groupId, entry) }
            }
        }
    }

    fun observeMembers(groupId: String): Flow<List<String>> =
        db.stadeDbQueries.selectGroupMembers(groupId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.contactId } }

    fun deleteGroup(groupId: String) {
        db.stadeDbQueries.transaction {
            db.stadeDbQueries.deleteGroupDeliveries(groupId)
            db.stadeDbQueries.deleteGroupMessages(groupId)
            db.stadeDbQueries.deleteGroupMembers(groupId)
            db.stadeDbQueries.deleteGroup(groupId)
            db.stadeDbQueries.deleteKv(chatWallpaperKey(groupId))
        }
        removeConversationShortcut(ShortcutEntityKind.GROUP, groupId)
    }

    fun removeMember(groupId: String, contactId: String) {
        db.stadeDbQueries.deleteGroupMember(groupId, contactId)
    }

    fun observeMessages(groupId: String): Flow<List<GroupMessage>> =
        db.stadeDbQueries.selectGroupMessages(groupId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toGroupMessage() } }

    fun observeLastMessage(groupId: String): Flow<GroupMessage?> =
        db.stadeDbQueries.selectLastGroupMessagePreview(groupId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toGroupMessage() }

    fun observeUnreadCount(groupId: String): Flow<Long> =
        db.stadeDbQueries.countGroupUnread(groupId)
            .asFlow()
            .mapToOne(Dispatchers.Default)

    fun lastMessage(groupId: String): GroupMessage? =
        db.stadeDbQueries.selectLastGroupMessagePreview(groupId).executeAsOneOrNull()?.toGroupMessage()

    fun unreadCount(groupId: String): Long =
        db.stadeDbQueries.countGroupUnread(groupId).executeAsOne()

    fun markRead(groupId: String) {
        db.stadeDbQueries.markGroupRead(groupId)
    }

    fun saveOutgoing(messageId: String, groupId: String, ownerStadeId: String, body: String, timestamp: Long) {
        if (db.stadeDbQueries.groupMessageExists(messageId).executeAsOne() > 0L) return
        db.stadeDbQueries.insertGroupMessage(messageId, groupId, ownerStadeId, body, timestamp, 1L, 1L)
    }

    fun deleteGroupMessages(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        db.stadeDbQueries.transaction {
            messageIds.forEach { id ->
                db.stadeDbQueries.deleteGroupMessageById(id)
            }
        }
    }

    fun leaveGroupLocally(groupId: String) {
        db.stadeDbQueries.transaction {
            db.stadeDbQueries.deleteGroupDeliveries(groupId)
            db.stadeDbQueries.deleteGroupMessages(groupId)
            db.stadeDbQueries.deleteGroupMembers(groupId)
            db.stadeDbQueries.deleteGroup(groupId)
            db.stadeDbQueries.deleteKv(chatWallpaperKey(groupId))
        }
        removeConversationShortcut(ShortcutEntityKind.GROUP, groupId)
    }

    fun handleIncomingGroupMsg(contactId: String, messageId: String, rawBody: String, timestamp: Long): String? {
        val stripped = rawBody.removePrefix(GRP_MSG_PREFIX)
        val colonIdx = stripped.indexOf(':')
        if (colonIdx < 0) return null
        val groupId = stripped.substring(0, colonIdx)
        val rest = stripped.substring(colonIdx + 1)
        val newlineIdx = rest.indexOf('\n')
        val senderId = if (newlineIdx >= 0) rest.substring(0, newlineIdx) else rest
        val body = if (newlineIdx >= 0) rest.substring(newlineIdx + 1) else ""

        if (senderId != contactId) return null

        val group = db.stadeDbQueries.selectGroup(groupId).executeAsOneOrNull() ?: return null
        val isMember = db.stadeDbQueries.isGroupMember(groupId, contactId).executeAsOne() > 0L
        if (!isMember) return null

        saveIncomingGroupMessage(groupId, messageId, senderId, body, timestamp)
        return groupId
    }

    fun handleJoinRequest(contactId: String, rawBody: String): String? {
        val stripped = rawBody.removePrefix(GRP_JOIN_PREFIX)
        val colonIdx = stripped.indexOf(':')
        if (colonIdx < 0) return null
        val groupId = stripped.substring(0, colonIdx)
        val inviteToken = stripped.substring(colonIdx + 1)

        val group = db.stadeDbQueries.selectGroup(groupId).executeAsOneOrNull() ?: return null
        if (group.inviteToken != inviteToken) return null

        addMember(groupId, contactId)

        val members = getMembers(groupId)
        val memberLine = members.joinToString("\n")
        return "$GRP_WELCOME_PREFIX$groupId:${group.name}\n$memberLine"
    }

    fun handleGroupWelcome(ownerId: String, creatorStadeId: String, rawBody: String) {
        val stripped = rawBody.removePrefix(GRP_WELCOME_PREFIX)
        val colonIdx = stripped.indexOf(':')
        if (colonIdx < 0) return
        val groupId = stripped.substring(0, colonIdx)
        val rest = stripped.substring(colonIdx + 1)
        val newlineIdx = rest.indexOf('\n')
        val groupName = if (newlineIdx >= 0) rest.substring(0, newlineIdx) else rest
        val memberIds = if (newlineIdx >= 0) {
            rest.substring(newlineIdx + 1).split('\n').filter { it.isNotBlank() }
        } else emptyList()

        val existing = db.stadeDbQueries.selectGroup(groupId).executeAsOneOrNull()
        if (existing == null) {
            val pending = getPendingJoinForContact(creatorStadeId)
            if (pending == null || pending.groupId != groupId) return
            val now = Clock.System.now().toEpochMilliseconds()
            val newToken = Encoding.toHex(crypto.randomBytes(16))
            db.stadeDbQueries.insertGroup(groupId, ownerId, groupName, newToken, now, creatorStadeId)
            clearPendingJoin(creatorStadeId)
        } else {
            if (existing.creatorStadeId.isNotBlank() && existing.creatorStadeId != creatorStadeId) return
            if (getPendingJoinForContact(creatorStadeId)?.groupId == groupId) clearPendingJoin(creatorStadeId)
        }
        memberIds.forEach { memberId ->
            runCatching { addMember(groupId, memberId) }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun encodeRoster(groupId: String, groupName: String, entries: List<GroupMemberEntry>): String {
        val lines = entries.filter { it.hasIdentity }.joinToString("\n") { entry ->
            listOf(
                entry.memberId,
                sanitizeRosterField(entry.nickname),
                Base64.Default.encode(entry.signingKey!!),
                Base64.Default.encode(entry.mldsaKey!!)
            ).joinToString(GRP_ROSTER_FIELD_SEP.toString())
        }
        return "$GRP_ROSTER_PREFIX$groupId:${sanitizeRosterField(groupName)}\n$lines"
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun parseRosterEntry(line: String): GroupMemberEntry? {
        val parts = line.split(GRP_ROSTER_FIELD_SEP)
        if (parts.size != 4) return null
        if (!StadeId.isValid(parts[0])) return null
        val signing = runCatching { Base64.Default.decode(parts[2]) }.getOrNull() ?: return null
        val mldsa = runCatching { Base64.Default.decode(parts[3]) }.getOrNull() ?: return null
        if (signing.size != 32 || mldsa.size != 1952) return null
        return GroupMemberEntry(parts[0], parts[1], signing, mldsa)
    }

    fun handleRoster(ownerId: String, fromContactId: String, rawBody: String): RosterUpdate? {
        val stripped = rawBody.removePrefix(GRP_ROSTER_PREFIX)
        val colonIdx = stripped.indexOf(':')
        if (colonIdx < 0) return null
        val groupId = stripped.substring(0, colonIdx)
        val rest = stripped.substring(colonIdx + 1)
        val newlineIdx = rest.indexOf('\n')
        val groupName = if (newlineIdx >= 0) rest.substring(0, newlineIdx) else rest
        val entryLines = if (newlineIdx >= 0) {
            rest.substring(newlineIdx + 1).split('\n').filter { it.isNotBlank() }
        } else emptyList()

        val existing = db.stadeDbQueries.selectGroup(groupId).executeAsOneOrNull()
        if (existing == null) {
            val pending = getPendingJoinForContact(fromContactId)
            if (pending == null || pending.groupId != groupId) return null
            val now = Clock.System.now().toEpochMilliseconds()
            val newToken = Encoding.toHex(crypto.randomBytes(16))
            db.stadeDbQueries.insertGroup(groupId, ownerId, groupName, newToken, now, fromContactId)
            clearPendingJoin(fromContactId)
        } else {
            if (existing.ownerId != ownerId) return null
            if (!isMember(groupId, fromContactId)) return null
            if (getPendingJoinForContact(fromContactId)?.groupId == groupId) clearPendingJoin(fromContactId)
        }
        val before = roster(groupId).toSet()
        entryLines.forEach { line ->
            val entry = parseRosterEntry(line) ?: return@forEach
            runCatching { setMemberIdentity(groupId, entry) }
        }
        return RosterUpdate(groupId, roster(groupId).toSet() != before)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun encodeFrame(frame: GroupFrame): String =
        GRP_FRAME_PREFIX + frame.groupId + ":" + frame.senderId + ":" + frame.messageId + ":" +
            frame.timestamp.toString() + ":" + frame.needsRelayTo.joinToString(GRP_NEEDS_SEP) + ":" +
            Base64.Default.encode(frame.signature) + ":" +
            (frame.pqSignature?.let { Base64.Default.encode(it) } ?: "") +
            "\n" + frame.payload

    fun signFrame(
        owner: LocalIdentity,
        groupId: String,
        messageId: String,
        timestamp: Long,
        needsRelayTo: List<String>,
        payload: String
    ): String {
        val material = groupSigningMaterial(
            groupId, owner.stadeId, messageId, timestamp, needsRelayTo, payload
        )
        val signature = crypto.sign(owner.privateSigningKey, material)
        val pqSignature = if (needsRelayTo.isEmpty()) null else runCatching {
            pq.signMlDsa(owner.privateMlDsaKey, owner.publicMlDsaKey, material)
        }.getOrNull()
        return encodeFrame(
            GroupFrame(
                groupId, owner.stadeId, messageId, timestamp, needsRelayTo,
                signature, pqSignature, payload
            )
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun parseFrame(rawBody: String): GroupFrame? {
        if (!rawBody.startsWith(GRP_FRAME_PREFIX)) return null
        val stripped = rawBody.substring(GRP_FRAME_PREFIX.length)
        val newlineIdx = stripped.indexOf('\n')
        if (newlineIdx < 0) return null
        val parts = stripped.substring(0, newlineIdx).split(':')
        if (parts.size != 7) return null
        if (parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) return null
        val timestamp = parts[3].toLongOrNull() ?: return null
        val needs = parts[4].split(GRP_NEEDS_SEP).filter { it.isNotBlank() }
        val signature = runCatching { Base64.Default.decode(parts[5]) }.getOrNull() ?: return null
        val pqSignature = if (parts[6].isEmpty()) null else
            runCatching { Base64.Default.decode(parts[6]) }.getOrNull() ?: return null
        return GroupFrame(
            parts[0], parts[1], parts[2], timestamp, needs, signature, pqSignature,
            stripped.substring(newlineIdx + 1)
        )
    }

    fun verifyFrame(frame: GroupFrame, requirePostQuantum: Boolean): Boolean {
        val entry = memberIdentity(frame.groupId, frame.senderId) ?: return false
        val signing = entry.signingKey ?: return false
        val mldsa = entry.mldsaKey ?: return false
        if (StadeId.derive(signing, mldsa, crypto::hash) != frame.senderId) return false
        return verifyFrameSignatures(frame, signing, mldsa, requirePostQuantum)
    }

    fun verifyFrameSignatures(
        frame: GroupFrame,
        signingKey: ByteArray,
        mldsaKey: ByteArray,
        requirePostQuantum: Boolean
    ): Boolean {
        val material = groupSigningMaterial(
            frame.groupId, frame.senderId, frame.messageId, frame.timestamp,
            frame.needsRelayTo, frame.payload
        )
        if (!crypto.verify(signingKey, material, frame.signature)) return false
        val pqSignature = frame.pqSignature ?: return !requirePostQuantum
        return runCatching { pq.verifyMlDsa(mldsaKey, material, pqSignature) }.getOrDefault(false)
    }

    fun recordDelivery(messageId: String, memberId: String): Boolean {
        val row = db.stadeDbQueries.selectGroupMessageById(messageId).executeAsOneOrNull() ?: return false
        if (row.outgoing != 1L) return false
        db.stadeDbQueries.insertGroupDelivery(messageId, memberId)
        return true
    }

    fun deliveryCounts(groupId: String): Map<String, Int> =
        db.stadeDbQueries.groupDeliveryCounts(groupId).executeAsList()
            .associate { it.messageId to it.deliveredCount.toInt() }

    fun observeDeliveryCounts(groupId: String): Flow<Map<String, Int>> =
        db.stadeDbQueries.groupDeliveryCounts(groupId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.associate { it.messageId to it.deliveredCount.toInt() } }

    fun saveIncomingGroupMessage(
        groupId: String,
        messageId: String,
        senderId: String,
        body: String,
        timestamp: Long
    ): Boolean {
        if (db.stadeDbQueries.groupMessageExists(messageId).executeAsOne() > 0L) return false
        db.stadeDbQueries.insertGroupMessage(messageId, groupId, senderId, body, timestamp, 0L, 0L)
        return true
    }

    fun handleKick(kickerContactId: String, rawBody: String, selfStadeId: String): KickOutcome? {
        val stripped = rawBody.removePrefix(GRP_KICK_PREFIX)
        val colonIdx = stripped.indexOf(':')
        if (colonIdx < 0) return null
        return applyKick(stripped.substring(0, colonIdx), kickerContactId, stripped.substring(colonIdx + 1), selfStadeId)
    }

    fun applyKick(groupId: String, kickerId: String, kickedId: String, selfStadeId: String): KickOutcome? {
        val group = db.stadeDbQueries.selectGroup(groupId).executeAsOneOrNull() ?: return null
        if (group.creatorStadeId.isBlank() || group.creatorStadeId != kickerId) return null

        return if (kickedId == selfStadeId) {
            val name = group.name
            leaveGroupLocally(groupId)
            KickOutcome(groupId, name, wasSelf = true)
        } else {
            removeMember(groupId, kickedId)
            KickOutcome(groupId, group.name, wasSelf = false)
        }
    }

    fun handleMemberLeft(leaverContactId: String, rawBody: String): String? =
        applyMemberLeft(rawBody.removePrefix(GRP_LEAVE_PREFIX), leaverContactId)

    fun applyMemberLeft(groupId: String, leaverId: String): String? {
        if (!isMember(groupId, leaverId)) return null
        removeMember(groupId, leaverId)
        return groupId
    }

    fun storePendingJoin(creatorStadeId: String, data: PendingJoinData) {
        pendingJoins[creatorStadeId] = data
        val json = "${data.groupId}\u0001${data.groupName}\u0001${data.inviteToken}"
        db.stadeDbQueries.putKv("grp.pending.$creatorStadeId", json.encodeToByteArray())
    }

    fun getPendingJoinForContact(contactStadeId: String): PendingJoinData? {
        if (pendingJoins.containsKey(contactStadeId)) return pendingJoins[contactStadeId]
        val raw = runCatching {
            db.stadeDbQueries.getKv("grp.pending.$contactStadeId").executeAsOneOrNull()
        }.getOrNull() ?: return null
        val parts = raw.decodeToString().split("\u0001")
        if (parts.size < 3) return null
        val data = PendingJoinData(parts[0], parts[1], parts[2])
        pendingJoins[contactStadeId] = data
        return data
    }

    fun clearPendingJoin(contactStadeId: String) {
        pendingJoins.remove(contactStadeId)
        runCatching { db.stadeDbQueries.deleteKv("grp.pending.$contactStadeId") }
    }

    fun generateInviteLink(groupId: String, groupName: String, inviteToken: String, creatorStadeId: String): String {
        val data = "$groupId\u0000$groupName\u0000$inviteToken\u0000$creatorStadeId"
        return "$GROUP_INVITE_PREFIX${Encoding.toBase32(data.encodeToByteArray())}"
    }

    fun parseInviteLink(code: String): GroupInviteData? {
        val upper = code.trim().uppercase()
        if (!upper.startsWith(GROUP_INVITE_PREFIX.uppercase())) return null
        val b32 = upper.substring(GROUP_INVITE_PREFIX.length)
        val bytes = runCatching { Encoding.fromBase32(b32) }.getOrNull() ?: return null
        val parts = bytes.decodeToString().split("\u0000")
        if (parts.size < 4) return null
        return GroupInviteData(parts[0], parts[1], parts[2], parts[3])
    }

    fun groupsForContact(contactId: String): List<String> =
        db.stadeDbQueries.memberGroupIds(contactId).executeAsList()

    fun searchInChat(groupId: String, query: String, limit: Long = 500): List<String> =
        db.stadeDbQueries.searchGroupMessagesInChat(groupId, query, limit).executeAsList()

    fun filterInChat(groupId: String, filter: SearchFilter, limit: Long = 500): List<String> {
        val prefixes = filter.prefixes ?: return emptyList()
        return db.stadeDbQueries
            .typedGroupMessagesInChat(groupId, prefixes.first, prefixes.second, limit)
            .executeAsList()
    }

    fun searchMessages(ownerId: String, query: String, limit: Long = 50): List<SearchResult> =
        db.stadeDbQueries.searchGroupMessages(ownerId, query, limit).executeAsList().map {
            SearchResult(it.id, it.groupId, true, it.groupName, previewBody(it.body, ""), it.timestamp)
        }

    private fun dev.stade.db.GroupMessage.toGroupMessage() = GroupMessage(
        id = id,
        groupId = groupId,
        senderId = senderId,
        body = body,
        timestamp = timestamp,
        isOwn = outgoing == 1L,
        isRead = read == 1L
    )

    private fun dev.stade.db.SelectLastGroupMessagePreview.toGroupMessage() = GroupMessage(
        id = id,
        groupId = groupId,
        senderId = senderId,
        body = body,
        timestamp = timestamp,
        isOwn = outgoing == 1L,
        isRead = read == 1L
    )
}

