package dev.stade.sync

import dev.stade.contact.Contact
import dev.stade.contact.ContactManager
import dev.stade.contact.HandshakeEphemeral
import dev.stade.contact.HandshakeService
import dev.stade.contact.InvitePayload
import dev.stade.contact.PROMOTE_TO_CONTACT_PREFIX
import dev.stade.contact.PeerEphemeral
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.Encoding
import dev.stade.crypto.PqCrypto
import dev.stade.crypto.RatchetSessions
import dev.stade.group.GRP_ACT_KICK
import dev.stade.group.GRP_ACT_LEAVE
import dev.stade.group.GRP_ACT_REACTION
import dev.stade.group.GRP_ACT_RECEIPT
import dev.stade.group.GRP_FRAME_PREFIX
import dev.stade.group.GRP_INV_PREFIX
import dev.stade.group.GRP_JOIN_PREFIX
import dev.stade.group.GRP_KICK_PREFIX
import dev.stade.group.GRP_LEAVE_PREFIX
import dev.stade.group.GRP_MSG_PREFIX
import dev.stade.group.GRP_ROSTER_PREFIX
import dev.stade.group.GRP_RXN_PREFIX
import dev.stade.group.GRP_WELCOME_PREFIX
import dev.stade.group.GROUP_PROTOCOL_VERSION
import dev.stade.group.GroupFrame
import dev.stade.group.GroupManager
import dev.stade.group.GroupMemberEntry
import dev.stade.identity.LocalIdentity
import dev.stade.identity.StadeId
import dev.stade.identity.sanitizeDisplayName
import dev.stade.message.AVATAR_BODY_PREFIX
import dev.stade.message.MAX_BIO_LEN
import dev.stade.message.MessageManager
import dev.stade.message.PROFILE_BODY_PREFIX
import dev.stade.message.REACTION_BODY_PREFIX
import dev.stade.message.TYPING_BODY_PREFIX
import dev.stade.message.VANISH_CANCEL_PREFIX
import dev.stade.message.VANISH_START_PREFIX
import dev.stade.message.parseAvatarBody
import dev.stade.message.parseProfileBody
import dev.stade.message.parseReactionWrapper
import dev.stade.message.parseTypingBody
import dev.stade.message.parseVanishCancelBody
import dev.stade.message.parseVanishStartBody
import dev.stade.message.parseVanishTag
import dev.stade.stadium.STD_ABOUT_PREFIX
import dev.stade.stadium.STD_COUNT_PREFIX
import dev.stade.stadium.STD_COUNT_REQUEST_PREFIX
import dev.stade.stadium.STD_DELETE_PREFIX
import dev.stade.stadium.STD_INV_PREFIX
import dev.stade.stadium.STD_MSG_DELETE_PREFIX
import dev.stade.stadium.STD_JOIN_PREFIX
import dev.stade.stadium.STD_LEAVE_PREFIX
import dev.stade.stadium.STD_MSG_PREFIX
import dev.stade.stadium.STD_WELCOME_PREFIX
import dev.stade.stadium.StadiumManager
import dev.stade.transport.Connection
import dev.stade.ui.i18n.I18n
import dev.stade.vanish.VanishManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json


class SyncEngine(
    private val crypto: CryptoApi,
    private val pq: PqCrypto,
    private val contacts: ContactManager,
    private val messages: MessageManager,
    private val ratchet: RatchetSessions,
    private val outbox: Outbox,
    private val handshakeService: HandshakeService,
    private val vanish: VanishManager,
    val groupManager: GroupManager? = null,
    val stadiumManager: StadiumManager? = null
) {
    private val protocolVersion = 4
    private val json = Json { ignoreUnknownKeys = true }
    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SyncEvent> = _events
    private val sessions = mutableMapOf<String, ContactSession>()
    private val sessionsLock = Mutex()
    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connectedContacts: StateFlow<Set<String>> = _connected.asStateFlow()
    private val _versionMismatch = MutableStateFlow<VersionMismatch?>(null)
    val peerVersionMismatch: StateFlow<VersionMismatch?> = _versionMismatch.asStateFlow()
    @Volatile var selfAddressesProvider: () -> List<String> = { emptyList() }
    @Volatile private var forgottenIds = emptySet<String>()
    @Volatile private var forgottenLoaded = false
    @Volatile private var pendingReAdds = emptySet<String>()
    private val pendingHandshakes = mutableMapOf<String, CompletableDeferred<Pair<Contact, Boolean>?>>()
    private val pendingHandshakesLock = Mutex()

    data class VersionMismatch(val peerId: String, val peerIsNewer: Boolean)

    sealed interface SyncEvent {
        data class ContactConnected(val contactId: String, val isNew: Boolean) : SyncEvent
        data class ContactDisconnected(val contactId: String) : SyncEvent
        data class MessageReceived(val contactId: String, val messageId: String) : SyncEvent
        data class GroupMessageReceived(val groupId: String) : SyncEvent
        data class GroupInviteReceived(val groupId: String, val groupName: String) : SyncEvent
        data class GroupMemberRemoved(val groupId: String) : SyncEvent
        data class GroupRosterUpdated(val groupId: String) : SyncEvent
        data class RemovedFromGroup(val groupId: String, val groupName: String) : SyncEvent
        data class HandshakeRejected(val reason: String, val peerId: String? = null) : SyncEvent
        data class DecryptFailed(val contactId: String) : SyncEvent
        data class SendFailed(val contactId: String, val reason: String) : SyncEvent
        data class ReactionUpdated(val messageId: String) : SyncEvent
        data class AvatarUpdated(val contactId: String) : SyncEvent
        data class ProfileUpdated(val contactId: String) : SyncEvent
        data class TypingChanged(val contactId: String, val typing: Boolean) : SyncEvent
        data class StadiumMessageReceived(val stadiumId: String) : SyncEvent
        data class StadiumContactReleased(val contactId: String, val forget: Boolean) : SyncEvent
        data class StadiumDeleted(val stadiumId: String) : SyncEvent
        data class StadiumMessageDeleted(val stadiumId: String, val messageId: String) : SyncEvent
        data class StadiumInviteReceived(val code: String) : SyncEvent
    }

    suspend fun queueOutgoing(owner: LocalIdentity, contact: Contact, messageId: String, body: String, timestamp: Long) {
        val sealed = try {
            ratchet.seal(owner, contact, body.encodeToByteArray())
        } catch (e: Throwable) {
            _events.tryEmit(SyncEvent.SendFailed(contact.id, e.message ?: e::class.simpleName ?: I18n.current.unknownError))
            return
        }
        val payload = MessagePayload(messageId, timestamp, sealed)
        val frame = json.encodeToString(MessagePayload.serializer(), payload).encodeToByteArray()
        if (frame.size > FrameCodec.MAX_LEN) {
            _events.tryEmit(SyncEvent.SendFailed(contact.id, "message too large to send"))
            return
        }
        outbox.enqueue(contact.id, messageId, frame)
        sessionsLock.withLock { sessions[contact.id] }?.notifyOutbox()
    }

    suspend fun handleConnection(owner: LocalIdentity, connection: Connection, outbound: Boolean = false): Boolean {
        var sessionStarted = false
        coroutineScope {
            val handshakeOutcome = handshake(owner, connection)
            if (handshakeOutcome == null) {
                connection.close()
                return@coroutineScope
            }
            val contact = handshakeOutcome.contact
            val isNew = handshakeOutcome.isNew
            val peerWireProtocol = handshakeOutcome.peerWireProtocol
            clearVersionMismatchFor(contact.id)
            contacts.markSeen(contact.id, Clock.System.now().toEpochMilliseconds())
            val preferOutbound = owner.stadeId < contact.id
            if (outbound != preferOutbound) {
                val existing = sessionsLock.withLock { sessions[contact.id]?.takeIf { it.isAlive() } }
                if (existing != null) {
                    runCatching { connection.close() }
                    return@coroutineScope
                }
            }
            val stale = sessionsLock.withLock { sessions[contact.id] }
            stale?.let { runCatching { it.cancelAndJoin() } }
            val session = sessionsLock.withLock {
                sessions[contact.id]?.let { runCatching { it.cancel() } }
                ContactSession(this@coroutineScope, owner, contact, connection, peerWireProtocol).also {
                    sessions[contact.id] = it
                    _connected.value = sessions.keys.toSet()
                }
            }
            _events.tryEmit(SyncEvent.ContactConnected(contact.id, isNew))
            sessionStarted = true
            try {
                session.run()
            } finally {
                withContext(NonCancellable) {
                    sessionsLock.withLock {
                        if (sessions[contact.id] === session) sessions.remove(contact.id)
                        _connected.value = sessions.keys.toSet()
                    }
                }
                _events.tryEmit(SyncEvent.ContactDisconnected(contact.id))
            }
        }
        return sessionStarted
    }

    private class Negotiated(val contact: Contact, val isNew: Boolean, val peerWireProtocol: Int)

    private suspend fun handshake(owner: LocalIdentity, connection: Connection): Negotiated? {
        val ourNonce = crypto.randomBytes(32)
        val ourEphemeral = handshakeService.newEphemeral()
        val ourTc = transcriptCommitment(
            protocolVersion,
            owner.publicSigningKey,
            owner.publicHandshakeKey,
            owner.publicMlKemKey,
            owner.publicMlDsaKey,
            ourEphemeral.dh.publicKey,
            ourEphemeral.kem.publicKey
        )
        val ourHello = HelloPayload(
            protocolVersion = protocolVersion,
            stadeId = owner.stadeId,
            nickname = owner.nickname,
            signingPublicKey = owner.publicSigningKey,
            handshakePublicKey = owner.publicHandshakeKey,
            mlkemPublicKey = owner.publicMlKemKey,
            mldsaPublicKey = owner.publicMlDsaKey,
            nonce = ourNonce,
            transcriptCommitment = ourTc,
            addresses = runCatching { selfAddressesProvider() }.getOrDefault(emptyList()),
            reAddRequest = pendingReAdds.isNotEmpty(),
            groupProtocol = GROUP_PROTOCOL_VERSION,
            wireProtocol = WIRE_PROTOCOL_VERSION,
            ephemeralHandshakeKey = ourEphemeral.dh.publicKey,
            ephemeralMlKemKey = ourEphemeral.kem.publicKey
        )
        runCatching {
            connection.send(FrameCodec.encode(SyncRecord(RecordType.HELLO, json.encodeToString(HelloPayload.serializer(), ourHello).encodeToByteArray())))
        }.getOrElse { return null }

        val helloFrame = withTimeoutOrNull(45_000) { connection.receive() } ?: return null
        val helloRecord = FrameCodec.decode(helloFrame) ?: return null
        if (helloRecord.type != RecordType.HELLO) return null
        val peerHello = runCatching {
            json.decodeFromString(HelloPayload.serializer(), helloRecord.payload.decodeToString())
        }.getOrNull() ?: return null

        if (peerHello.protocolVersion != protocolVersion) {
            val peerIsNewer = peerHello.protocolVersion > protocolVersion
            val known = runCatching { contacts.findByStadeId(peerHello.stadeId) }.getOrNull()
            if (known != null && known.kind == 0 && known.ownerId == owner.id) {
                _versionMismatch.value = VersionMismatch(peerHello.stadeId, peerIsNewer)
            }
            _events.tryEmit(
                SyncEvent.HandshakeRejected(
                    if (peerIsNewer) I18n.current.updateRequiredByYou
                    else I18n.current.updateRequiredByPeer,
                    peerHello.stadeId
                )
            )
            return null
        }
        if (peerHello.signingPublicKey.size != 32 ||
            peerHello.handshakePublicKey.size != 32 ||
            peerHello.mlkemPublicKey.size != 1184 ||
            peerHello.mldsaPublicKey.size != 1952 ||
            peerHello.ephemeralHandshakeKey.size != 32 ||
            peerHello.ephemeralMlKemKey.size != 1184
        ) {
            _events.tryEmit(SyncEvent.HandshakeRejected(I18n.current.hsKeySizeBad, peerHello.stadeId))
            return null
        }
        if (peerHello.signingPublicKey.contentEquals(owner.publicSigningKey)) {
            _events.tryEmit(SyncEvent.HandshakeRejected(I18n.current.hsSelfConnected, peerHello.stadeId))
            return null
        }

        val derivedPeerId = StadeId.derive(peerHello.signingPublicKey, peerHello.mldsaPublicKey, crypto::hash)
        if (derivedPeerId != peerHello.stadeId) {
            _events.tryEmit(SyncEvent.HandshakeRejected(I18n.current.hsStadeIdMismatch, peerHello.stadeId))
            return null
        }
        if (peerHello.stadeId in forgotten()) {
            if (!peerHello.reAddRequest) return null
            unforget(peerHello.stadeId)
        }

        val peerEphemeral = PeerEphemeral(peerHello.ephemeralHandshakeKey, peerHello.ephemeralMlKemKey)
        val expectedPeerTc = transcriptCommitment(
            protocolVersion,
            peerHello.signingPublicKey,
            peerHello.handshakePublicKey,
            peerHello.mlkemPublicKey,
            peerHello.mldsaPublicKey,
            peerEphemeral.dhPub,
            peerEphemeral.kemPub
        )
        if (!expectedPeerTc.contentEquals(peerHello.transcriptCommitment)) {
            _events.tryEmit(SyncEvent.HandshakeRejected(I18n.current.hsTranscriptMismatch, peerHello.stadeId))
            return null
        }

        val existingBeforeAuth = contacts.findByStadeId(peerHello.stadeId)
            ?: contacts.findByPublicKey(peerHello.signingPublicKey)

        if (existingBeforeAuth != null &&
            (!existingBeforeAuth.publicSigningKey.contentEquals(peerHello.signingPublicKey) ||
                !existingBeforeAuth.publicMlDsaKey.contentEquals(peerHello.mldsaPublicKey))
        ) {
            _events.tryEmit(
                SyncEvent.HandshakeRejected(I18n.current.hsIdentityKeyMismatch, peerHello.stadeId)
            )
            return null
        }

        val authMessage = AUTH_PREFIX + peerHello.nonce + ourTc + peerHello.transcriptCommitment
        val ourEdSig = crypto.sign(owner.privateSigningKey, authMessage)
        val ourDsaSig = pq.signMlDsa(owner.privateMlDsaKey, owner.publicMlDsaKey, authMessage)
        val weAreStadiumJoining = stadiumManager?.getPendingJoinForContact(peerHello.stadeId) != null
        val ourAuth = AuthPayload(
            owner.stadeId, ourEdSig, ourDsaSig,
            isStadiumJoin = weAreStadiumJoining,
            noExistingContact = existingBeforeAuth == null
        )
        runCatching {
            connection.send(FrameCodec.encode(SyncRecord(RecordType.AUTH, json.encodeToString(AuthPayload.serializer(), ourAuth).encodeToByteArray())))
        }.getOrElse { return null }

        val authFrame = withTimeoutOrNull(45_000) { connection.receive() } ?: return null
        val authRecord = FrameCodec.decode(authFrame) ?: return null
        if (authRecord.type != RecordType.AUTH) return null
        val peerAuth = runCatching {
            json.decodeFromString(AuthPayload.serializer(), authRecord.payload.decodeToString())
        }.getOrNull() ?: return null
        if (peerAuth.stadeId != peerHello.stadeId) {
            _events.tryEmit(SyncEvent.HandshakeRejected(I18n.current.hsAuthStadeIdMismatch, peerHello.stadeId))
            return null
        }

        val peerAuthMessage = AUTH_PREFIX + ourNonce + peerHello.transcriptCommitment + ourTc
        val edOk = crypto.verify(peerHello.signingPublicKey, peerAuthMessage, peerAuth.edSignature)
        val dsaOk = pq.verifyMlDsa(peerHello.mldsaPublicKey, peerAuthMessage, peerAuth.mldsaSignature)
        if (!edOk || !dsaOk) {
            _events.tryEmit(SyncEvent.HandshakeRejected(
                if (!edOk && !dsaOk) I18n.current.hsSignaturesInvalid
                else if (!edOk) I18n.current.hsEdInvalid
                else I18n.current.hsMldsaInvalid,
                peerHello.stadeId
            ))
            return null
        }

        val existing = existingBeforeAuth
        if (existing != null && !peerAuth.noExistingContact) {
            if (existing.ownerId != owner.id) return null
            if (peerHello.addresses.isNotEmpty()) {
                val nonLan = (existing.addresses + peerHello.addresses).filter { !it.startsWith("lan://") }
                val currentLan = peerHello.addresses.filter { it.startsWith("lan://") }
                val merged = (nonLan + currentLan).distinct()
                if (merged != existing.addresses) {
                    runCatching { contacts.setAddresses(existing.id, merged) }
                }
            }
            pendingReAdds = pendingReAdds - peerHello.stadeId
            noteGroupProtocol(existing.id, peerHello.groupProtocol)
            return Negotiated(existing, false, peerHello.wireProtocol)
        }

        var priorKind: Int? = null
        var priorVerified = false
        if (existing != null && peerAuth.noExistingContact) {
            if (existing.ownerId != owner.id) return null
            priorKind = existing.kind
            priorVerified = existing.verified
            runCatching { ratchet.forget(existing.id) }
            runCatching { contacts.delete(existing.id) }
        }

        val established = gatedNewContactHandshake(
            owner, connection, peerHello, peerAuth.isStadiumJoin, priorKind, ourEphemeral, peerEphemeral
        )
        if (established == null) return null
        if (priorVerified) runCatching { contacts.verify(established.first.id) }
        pendingReAdds = pendingReAdds - peerHello.stadeId
        noteGroupProtocol(established.first.id, peerHello.groupProtocol)
        return Negotiated(established.first, established.second, peerHello.wireProtocol)
    }

    private val rosterPushedAhead = mutableSetOf<String>()

    private fun markRosterPushed(contactId: String, groupId: String): Boolean =
        synchronized(rosterPushedAhead) { rosterPushedAhead.add("$contactId|$groupId") }

    private fun rosterEntryFromContact(memberId: String): GroupMemberEntry? =
        contacts.get(memberId)?.takeIf { it.kind == 0 }?.let {
            GroupMemberEntry(it.id, "", it.publicSigningKey, it.publicMlDsaKey)
        }

    private fun noteGroupProtocol(contactId: String, advertised: Int) {
        val supported = advertised.coerceIn(1, GROUP_PROTOCOL_VERSION)
        runCatching {
            if (contacts.get(contactId)?.groupProto != supported) contacts.setGroupProto(contactId, supported)
        }
    }

    private suspend fun gatedNewContactHandshake(
        owner: LocalIdentity,
        connection: Connection,
        peerHello: HelloPayload,
        peerClaimsStadiumJoin: Boolean,
        priorKind: Int?,
        ourEphemeral: HandshakeEphemeral,
        peerEphemeral: PeerEphemeral
    ): Pair<Contact, Boolean>? {
        val gateKey = peerHello.stadeId
        val (isLeader, gate) = pendingHandshakesLock.withLock {
            pendingHandshakes[gateKey]?.let { false to it }
                ?: CompletableDeferred<Pair<Contact, Boolean>?>().also { pendingHandshakes[gateKey] = it }.let { true to it }
        }
        if (!isLeader) {
            return withTimeoutOrNull(50_000) { gate.await() }
        }
        try {
            val freshExisting = contacts.findByStadeId(peerHello.stadeId)?.takeIf { it.ownerId == owner.id }
            val result = if (freshExisting != null) {
                freshExisting to false
            } else {
                performNewContactHandshake(
                    owner, connection, peerHello, peerClaimsStadiumJoin, priorKind, ourEphemeral, peerEphemeral
                )
            }
            gate.complete(result)
            return result
        } catch (e: CancellationException) {
            gate.complete(null)
            throw e
        } catch (e: Throwable) {
            gate.complete(null)
            return null
        } finally {
            pendingHandshakesLock.withLock {
                if (pendingHandshakes[gateKey] === gate) pendingHandshakes.remove(gateKey)
            }
        }
    }

    private suspend fun performNewContactHandshake(
        owner: LocalIdentity,
        connection: Connection,
        peerHello: HelloPayload,
        peerClaimsStadiumJoin: Boolean,
        priorKind: Int?,
        ourEphemeral: HandshakeEphemeral,
        peerEphemeral: PeerEphemeral
    ): Pair<Contact, Boolean>? {
        val invite = InvitePayload(
            stadeId = peerHello.stadeId,
            nickname = sanitizeDisplayName(peerHello.nickname).ifBlank { I18n.current.unknownNickname },
            signingPublicKey = peerHello.signingPublicKey,
            handshakePublicKey = peerHello.handshakePublicKey,
            mlkemPublicKey = peerHello.mlkemPublicKey,
            mldsaPublicKey = peerHello.mldsaPublicKey,
            addresses = peerHello.addresses
        )
        val isAlice = handshakeService.isAlice(owner, invite)
        val kemCt: ByteArray
        val kemSs: ByteArray
        if (isAlice) {
            val enc = handshakeService.encapsulateForPeer(peerEphemeral.kemPub)
            kemCt = enc.ciphertext
            kemSs = enc.sharedSecret
            val rec = SyncRecord(RecordType.KEM_OFFER,
                json.encodeToString(KemOfferPayload.serializer(), KemOfferPayload(kemCt)).encodeToByteArray())
            runCatching { connection.send(FrameCodec.encode(rec)) }.getOrElse { return null }
        } else {
            val frame = withTimeoutOrNull(45_000) { connection.receive() } ?: return null
            val rec = FrameCodec.decode(frame) ?: return null
            if (rec.type != RecordType.KEM_OFFER) return null
            val offer = runCatching {
                json.decodeFromString(KemOfferPayload.serializer(), rec.payload.decodeToString())
            }.getOrNull() ?: return null
            kemCt = offer.ciphertext
            kemSs = runCatching { handshakeService.decapsulate(ourEphemeral, kemCt) }.getOrNull() ?: run {
                _events.tryEmit(SyncEvent.HandshakeRejected(I18n.current.hsMlkemDecapFailed, peerHello.stadeId))
                return null
            }
        }

        val rootKey = runCatching {
            handshakeService.deriveRootKey(owner, invite, ourEphemeral, peerEphemeral, kemCt, kemSs)
        }.getOrNull() ?: return null

        val nickname = sanitizeDisplayName(peerHello.nickname)
            .ifBlank { I18n.current.contactNameFallback(peerHello.stadeId.takeLast(4)) }
        val weAreStadiumJoining = stadiumManager?.getPendingJoinForContact(peerHello.stadeId) != null
        val reAdd = peerHello.reAddRequest || peerHello.stadeId in pendingReAdds
        val stadiumKind = when {
            weAreStadiumJoining || peerClaimsStadiumJoin -> 1
            priorKind == 1 -> 1
            priorKind == 2 && !reAdd -> 2
            else -> 0
        }
        val newContact = runCatching {
            contacts.addFromHandshake(
                owner = owner,
                nickname = nickname,
                peerSigningKey = peerHello.signingPublicKey,
                peerHandshakeKey = peerHello.handshakePublicKey,
                peerMlKemKey = peerHello.mlkemPublicKey,
                peerMlDsaKey = peerHello.mldsaPublicKey,
                rootKey = rootKey,
                isAlice = isAlice,
                addresses = peerHello.addresses,
                kind = stadiumKind
            )
        }.getOrNull() ?: contacts.findByStadeId(peerHello.stadeId) ?: return null
        return newContact to true
    }

    private fun transcriptCommitment(
        proto: Int,
        edPub: ByteArray,
        xPub: ByteArray,
        kemPub: ByteArray,
        dsaPub: ByteArray,
        ephDhPub: ByteArray,
        ephKemPub: ByteArray
    ): ByteArray {
        val out = ByteArray(4)
        out[0] = ((proto ushr 24) and 0xff).toByte()
        out[1] = ((proto ushr 16) and 0xff).toByte()
        out[2] = ((proto ushr 8) and 0xff).toByte()
        out[3] = (proto and 0xff).toByte()
        return crypto.hash(TC_PREFIX + out + edPub + xPub + kemPub + dsaPub + ephDhPub + ephKemPub)
    }

    companion object {
        private val AUTH_PREFIX = "stade-auth-v3".encodeToByteArray()
        private val TC_PREFIX = "stade-tc-v3".encodeToByteArray()
    }

    private inner class ContactSession(
        private val parent: CoroutineScope,
        private val owner: LocalIdentity,
        private val contact: Contact,
        @Volatile private var connection: Connection,
        private val peerWireProtocol: Int
    ) {
        private val outboxSignal = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 8)
        private var rootJob: Job? = null
        @Volatile private var lastRxAt = Clock.System.now().toEpochMilliseconds()

        fun notifyOutbox() { outboxSignal.tryEmit(Unit) }

        fun isAlive(): Boolean = rootJob?.isActive != false

        fun cancel() { rootJob?.cancel() }

        suspend fun cancelAndJoin() {
            val job = rootJob
            if (job != null) {
                job.cancel()
                runCatching { job.join() }
            }
        }

        suspend fun run() = coroutineScope {
            rootJob = coroutineContext[Job]
            val sender = launch { runSender(this@coroutineScope) }
            val receiver = launch { runReceiver(this@coroutineScope) }
            val pinger = launch { runPinger() }
            val watchdog = launch { runWatchdog() }
            try {
                receiver.join()
            } finally {
                sender.cancel()
                pinger.cancel()
                watchdog.cancel()
                runCatching { connection.close() }
            }
        }

        private suspend fun runSender(scope: CoroutineScope) {
            if (!drainOutbox(scope)) return
            outboxSignal.collect {
                if (!scope.isActive) return@collect
                if (!drainOutbox(scope)) return@collect
            }
        }


        private fun toWireRecord(stored: ByteArray): SyncRecord {
            if (peerWireProtocol < WIRE_PROTOCOL_VERSION) {
                return SyncRecord(RecordType.MESSAGE, stored)
            }
            val parsed = runCatching {
                json.decodeFromString(MessagePayload.serializer(), stored.decodeToString())
            }.getOrNull() ?: return SyncRecord(RecordType.MESSAGE, stored)
            val binary = encodeBinaryPayload(parsed) ?: return SyncRecord(RecordType.MESSAGE, stored)
            return SyncRecord(RecordType.MESSAGE_BIN, binary)
        }

        private suspend fun drainOutbox(scope: CoroutineScope): Boolean {
            val pendingIds = runCatching { outbox.pendingIds(contact.id) }.getOrNull() ?: return true
            for (id in pendingIds) {
                if (!scope.isActive) return false
                val item = runCatching { outbox.item(id) }.getOrNull() ?: continue
                if (item.payload.size > FrameCodec.MAX_LEN) {
                    outbox.remove(item.id)
                    _events.tryEmit(SyncEvent.SendFailed(contact.id, "message too large to send"))
                    continue
                }
                val record = toWireRecord(item.payload)
                if (record.payload.size > FrameCodec.MAX_LEN) {
                    outbox.remove(item.id)
                    _events.tryEmit(SyncEvent.SendFailed(contact.id, "message too large to send"))
                    continue
                }
                val ok = runCatching {
                    connection.send(FrameCodec.encode(record))
                }.isSuccess
                if (!ok) {
                    scope.cancel()
                    return false
                }
                outbox.bump(item.id)
            }
            return true
        }

        private suspend fun runReceiver(scope: CoroutineScope) {
            while (scope.isActive) {
                val frame = runCatching { connection.receive() }.getOrNull() ?: return
                lastRxAt = Clock.System.now().toEpochMilliseconds()
                val record = FrameCodec.decode(frame) ?: continue
                try {
                    handleRecord(record)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                }
            }
        }

        private suspend fun runPinger() {
            try {
                while (true) {
                    delay(30_000)
                    runCatching {
                        connection.send(FrameCodec.encode(SyncRecord(RecordType.PING, ByteArray(0))))
                    }.getOrElse {
                        runCatching { connection.close() }
                        return
                    }
                }
            } catch (_: CancellationException) {
            }
        }

        private suspend fun runWatchdog() {
            try {
                while (true) {
                    delay(15_000)
                    if (Clock.System.now().toEpochMilliseconds() - lastRxAt > 95_000) {
                        runCatching { connection.close() }
                        return
                    }
                }
            } catch (_: CancellationException) {
            }
        }

        private fun peerGroupProto(contactId: String): Int =
            runCatching { contacts.get(contactId)?.groupProto }.getOrNull() ?: 1

        private suspend fun handleGroupFrame(
            groups: GroupManager,
            rawFrame: String,
            envelopeId: String,
            envelopeTimestamp: Long
        ): Boolean {
            val frame = groups.parseFrame(rawFrame) ?: return true
            if (frame.messageId != envelopeId || frame.timestamp != envelopeTimestamp) return true
            if (frame.senderId == owner.stadeId) return true
            if (groups.getGroup(frame.groupId)?.ownerId != owner.id) return true
            if (!groups.isMember(frame.groupId, contact.id)) return true
            if (!groups.isMember(frame.groupId, frame.senderId)) return true

            if (groups.memberIdentity(frame.groupId, frame.senderId)?.hasIdentity != true) {
                val adopted = rosterEntryFromContact(frame.senderId)?.let {
                    runCatching { groups.setMemberIdentity(frame.groupId, it) }.getOrDefault(false)
                } ?: false
                if (!adopted) return false
            }
            if (!groups.verifyFrame(frame, requirePostQuantum = contact.id != frame.senderId)) return true

            val payload = frame.payload
            val accepted = when {
                payload.startsWith(GRP_ACT_REACTION) -> {
                    val wrapper = parseReactionWrapper(payload.removePrefix(GRP_ACT_REACTION))
                    if (wrapper == null) false else {
                        if (wrapper.add) messages.upsertReaction(wrapper.targetMessageId, frame.senderId, wrapper.emoji)
                        else messages.deleteReaction(wrapper.targetMessageId, frame.senderId)
                        _events.tryEmit(SyncEvent.ReactionUpdated(wrapper.targetMessageId))
                        true
                    }
                }
                payload.startsWith(GRP_ACT_RECEIPT) -> {
                    val target = payload.removePrefix(GRP_ACT_RECEIPT)
                    if (target.isEmpty()) false else {
                        runCatching { groups.recordDelivery(target, frame.senderId) }
                        true
                    }
                }
                payload.startsWith(GRP_ACT_KICK) -> {
                    val outcome = groups.applyKick(
                        frame.groupId, frame.senderId, payload.removePrefix(GRP_ACT_KICK), owner.stadeId
                    )
                    if (outcome == null) false else {
                        _events.tryEmit(
                            if (outcome.wasSelf) SyncEvent.RemovedFromGroup(outcome.groupId, outcome.groupName)
                            else SyncEvent.GroupMemberRemoved(outcome.groupId)
                        )
                        true
                    }
                }
                payload == GRP_ACT_LEAVE -> {
                    val groupId = groups.applyMemberLeft(frame.groupId, frame.senderId)
                    if (groupId == null) false else {
                        _events.tryEmit(SyncEvent.GroupMemberRemoved(groupId))
                        true
                    }
                }
                else -> {
                    val saved = groups.saveIncomingGroupMessage(
                        frame.groupId, frame.messageId, frame.senderId, payload, frame.timestamp
                    )
                    if (saved) {
                        _events.tryEmit(SyncEvent.GroupMessageReceived(frame.groupId))
                        if (contact.id != frame.senderId) sendDeliveryReceipt(groups, frame)
                    }
                    saved
                }
            }
            if (accepted) relayGroupFrame(groups, frame, rawFrame)
            return true
        }

        private fun relayTarget(memberId: String): Contact? =
            contacts.get(memberId)
                ?.takeIf { it.ownerId == owner.id && it.groupProto >= GROUP_PROTOCOL_VERSION }

        private suspend fun sendDeliveryReceipt(groups: GroupManager, frame: GroupFrame) {
            val via = relayTarget(contact.id) ?: return
            val messageId = Encoding.toHex(crypto.randomBytes(16))
            val timestamp = Clock.System.now().toEpochMilliseconds()
            val signed = groups.signFrame(
                owner, frame.groupId, messageId, timestamp,
                listOf(frame.senderId), GRP_ACT_RECEIPT + frame.messageId
            )
            runCatching { queueOutgoing(owner, via, messageId, signed, timestamp) }
        }

        private suspend fun relayGroupFrame(groups: GroupManager, frame: GroupFrame, rawFrame: String) {
            if (frame.needsRelayTo.isEmpty()) return
            val members = groups.getMembers(frame.groupId)
            val skip = setOf(owner.stadeId, contact.id, frame.senderId)
            val pending = frame.needsRelayTo.filter { it in members && it !in skip }
            if (pending.isEmpty()) return
            val reachable = pending.filter { relayTarget(it) != null }
            val targets = if (reachable.size == pending.size) reachable else members.filter { it !in skip }
            for (memberId in targets) {
                val member = relayTarget(memberId) ?: continue
                pushRosterAhead(groups, member, frame.groupId)
                runCatching { queueOutgoing(owner, member, frame.messageId, rawFrame, frame.timestamp) }
            }
        }

        private suspend fun pushRosterAhead(groups: GroupManager, member: Contact, groupId: String) {
            if (!markRosterPushed(member.id, groupId)) return
            val group = groups.getGroup(groupId) ?: return
            val entries = groups.rosterSnapshot(owner, groupId, ::rosterEntryFromContact)
            if (entries.isEmpty()) return
            val body = groups.encodeRoster(group.id, group.name, entries)
            val msgId = Encoding.toHex(crypto.randomBytes(16))
            runCatching {
                queueOutgoing(owner, member, msgId, body, Clock.System.now().toEpochMilliseconds())
            }
        }

        private suspend fun handleRecord(record: SyncRecord) {
            when (record.type) {
                RecordType.MESSAGE, RecordType.MESSAGE_BIN -> {
                    val payload = (
                        if (record.type == RecordType.MESSAGE_BIN) decodeBinaryPayload(record.payload)
                        else runCatching {
                            json.decodeFromString(MessagePayload.serializer(), record.payload.decodeToString())
                        }.getOrNull()
                    ) ?: return
                    if (messages.isEnvelopeProcessed(payload.messageId)) {
                        val ack = AckPayload(payload.messageId)
                        runCatching {
                            connection.send(FrameCodec.encode(SyncRecord(RecordType.ACK, json.encodeToString(AckPayload.serializer(), ack).encodeToByteArray())))
                        }
                        return
                    }
                    val plain = ratchet.open(owner, contact, payload.ratchetFrame)
                    if (plain == null) {
                        _events.tryEmit(SyncEvent.DecryptFailed(contact.id))
                        return
                    }
                    messages.markEnvelopeProcessed(payload.messageId, contact.id, Clock.System.now().toEpochMilliseconds())
                    val bodyStr = plain.decodeToString()

                    when {
                        bodyStr == PROMOTE_TO_CONTACT_PREFIX -> {
                            runCatching {
                                val fresh = contacts.get(contact.id)
                                if (fresh != null && fresh.kind != 0) contacts.setKind(contact.id, 0)
                            }
                        }
                        bodyStr.startsWith(REACTION_BODY_PREFIX) -> {
                            val wrapper = if ((contacts.get(contact.id)?.kind ?: 0) == 0) parseReactionWrapper(bodyStr) else null
                            if (wrapper != null) {
                                if (wrapper.add) messages.upsertReaction(wrapper.targetMessageId, contact.id, wrapper.emoji)
                                else messages.deleteReaction(wrapper.targetMessageId, contact.id)
                                _events.tryEmit(SyncEvent.ReactionUpdated(wrapper.targetMessageId))
                            }
                        }
                        bodyStr.startsWith(AVATAR_BODY_PREFIX) -> {
                            if ((contacts.get(contact.id)?.kind ?: 0) == 0) {
                                contacts.setAvatar(contact.id, parseAvatarBody(bodyStr))
                                _events.tryEmit(SyncEvent.AvatarUpdated(contact.id))
                            }
                        }
                        bodyStr.startsWith(PROFILE_BODY_PREFIX) -> {
                            if ((contacts.get(contact.id)?.kind ?: 0) == 0) {
                                val parsed = parseProfileBody(bodyStr)
                                if (parsed != null) {
                                    val (status, bio) = parsed
                                    contacts.setProfile(
                                        contact.id,
                                        sanitizeDisplayName(bio, MAX_BIO_LEN),
                                        status
                                    )
                                    _events.tryEmit(SyncEvent.ProfileUpdated(contact.id))
                                }
                            }
                        }
                        bodyStr.startsWith(TYPING_BODY_PREFIX) -> {
                            val typing = if ((contacts.get(contact.id)?.kind ?: 0) == 0) parseTypingBody(bodyStr) else null
                            if (typing != null) _events.tryEmit(SyncEvent.TypingChanged(contact.id, typing))
                        }
                        bodyStr.startsWith(VANISH_START_PREFIX) -> {
                            val wrapper = if ((contacts.get(contact.id)?.kind ?: 0) == 0) parseVanishStartBody(bodyStr) else null
                            if (wrapper != null) {
                                vanish.withLock(contact.id) {
                                    vanish.adoptRemoteStartLocked(
                                        contact.id, wrapper.sessionId, wrapper.startedAt, wrapper.durationMs,
                                        Clock.System.now().toEpochMilliseconds()
                                    )
                                }
                            }
                        }
                        bodyStr.startsWith(VANISH_CANCEL_PREFIX) -> {
                            val sessionId = if ((contacts.get(contact.id)?.kind ?: 0) == 0) parseVanishCancelBody(bodyStr) else null
                            if (sessionId != null) {
                                vanish.withLock(contact.id) {
                                    vanish.cancelSessionLocked(contact.id, sessionId)
                                }
                            }
                        }
                        groupManager != null && bodyStr.startsWith(GRP_FRAME_PREFIX) -> {
                            if (!handleGroupFrame(groupManager, bodyStr, payload.messageId, payload.timestamp)) {
                                runCatching { messages.forgetEnvelope(payload.messageId) }
                                return
                            }
                        }
                        groupManager != null && bodyStr.startsWith(GRP_ROSTER_PREFIX) -> {
                            val update = groupManager.handleRoster(owner.id, contact.id, bodyStr)
                            if (update != null) {
                                runCatching {
                                    groupManager.setMemberIdentity(update.groupId, groupManager.selfRosterEntry(owner))
                                }
                                groupManager.adoptIdentities(owner, update.groupId, ::rosterEntryFromContact)
                                if (update.changed) _events.tryEmit(SyncEvent.GroupRosterUpdated(update.groupId))
                            }
                        }
                        groupManager != null && bodyStr.startsWith(GRP_RXN_PREFIX) -> {
                            val stripped = bodyStr.removePrefix(GRP_RXN_PREFIX)
                            val colonIdx = stripped.indexOf(':')
                            if (colonIdx >= 0 && stripped.substring(0, colonIdx) in groupManager.groupsForContact(contact.id)) {
                                val wrapper = parseReactionWrapper(stripped.substring(colonIdx + 1))
                                if (wrapper != null) {
                                    if (wrapper.add) messages.upsertReaction(wrapper.targetMessageId, contact.id, wrapper.emoji)
                                    else messages.deleteReaction(wrapper.targetMessageId, contact.id)
                                    _events.tryEmit(SyncEvent.ReactionUpdated(wrapper.targetMessageId))
                                }
                            }
                        }
                        groupManager != null && bodyStr.startsWith(GRP_MSG_PREFIX) -> {
                            val groupId = groupManager.handleIncomingGroupMsg(contact.id, payload.messageId, bodyStr, payload.timestamp)
                            if (groupId != null) _events.tryEmit(SyncEvent.GroupMessageReceived(groupId))
                        }
                        groupManager != null && bodyStr.startsWith(GRP_JOIN_PREFIX) -> {
                            val welcomeBody = groupManager.handleJoinRequest(contact.id, bodyStr)
                            if (welcomeBody != null) {
                                val joinedGroupId = bodyStr.removePrefix(GRP_JOIN_PREFIX).substringBefore(':')
                                runCatching {
                                    groupManager.setMemberIdentity(joinedGroupId, groupManager.selfRosterEntry(owner))
                                }
                                groupManager.adoptIdentities(owner, joinedGroupId, ::rosterEntryFromContact)
                                val rosterBody = groupManager.getGroup(joinedGroupId)?.let { joined ->
                                    val entries = groupManager.rosterSnapshot(owner, joined.id, ::rosterEntryFromContact)
                                    if (entries.isEmpty()) null else groupManager.encodeRoster(joined.id, joined.name, entries)
                                }
                                runCatching {
                                    val msgId = Encoding.toHex(crypto.randomBytes(16))
                                    val ts = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                    val joinerBody = if (rosterBody != null && peerGroupProto(contact.id) >= GROUP_PROTOCOL_VERSION) rosterBody else welcomeBody
                                    val sealed = ratchet.seal(owner, contact, joinerBody.encodeToByteArray())
                                    val mp = MessagePayload(msgId, ts, sealed)
                                    val frame = json.encodeToString(MessagePayload.serializer(), mp).encodeToByteArray()
                                    outbox.enqueue(contact.id, msgId, frame)
                                    outboxSignal.tryEmit(Unit)
                                }
                                val ts2 = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                for (memberId in groupManager.getMembers(joinedGroupId)) {
                                    if (memberId == contact.id) continue
                                    val member = contacts.get(memberId) ?: continue
                                    if (member.ownerId != owner.id) continue
                                    val body = if (rosterBody != null && member.groupProto >= GROUP_PROTOCOL_VERSION) rosterBody else welcomeBody
                                    val msgId2 = Encoding.toHex(crypto.randomBytes(16))
                                    runCatching { queueOutgoing(owner, member, msgId2, body, ts2) }
                                }
                            }
                        }
                        groupManager != null && bodyStr.startsWith(GRP_WELCOME_PREFIX) -> {
                            groupManager.handleGroupWelcome(owner.id, contact.id, bodyStr)
                            val welcomedGroupId = bodyStr.removePrefix(GRP_WELCOME_PREFIX).substringBefore(':')
                            if (groupManager.getGroup(welcomedGroupId) != null) {
                                runCatching {
                                    groupManager.setMemberIdentity(welcomedGroupId, groupManager.selfRosterEntry(owner))
                                }
                                groupManager.adoptIdentities(owner, welcomedGroupId, ::rosterEntryFromContact)
                            }
                        }
                        groupManager != null && bodyStr.startsWith(GRP_INV_PREFIX) -> {
                            runCatching {
                                if ((contacts.get(contact.id)?.kind ?: 0) != 0) return@runCatching
                                val inviteCode = bodyStr.removePrefix(GRP_INV_PREFIX)
                                val parsed = groupManager.parseInviteLink(inviteCode) ?: return@runCatching
                                val pending = dev.stade.group.PendingJoinData(
                                    parsed.groupId, parsed.groupName, parsed.inviteToken
                                )
                                groupManager.storePendingJoin(parsed.creatorStadeId, pending)
                                val msgId2 = Encoding.toHex(crypto.randomBytes(16))
                                val ts2 = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                val joinBody = "$GRP_JOIN_PREFIX${parsed.groupId}:${parsed.inviteToken}"
                                val sealed = ratchet.seal(owner, contact, joinBody.encodeToByteArray())
                                val mp = MessagePayload(msgId2, ts2, sealed)
                                val frame = json.encodeToString(MessagePayload.serializer(), mp).encodeToByteArray()
                                outbox.enqueue(contact.id, msgId2, frame)
                                outboxSignal.tryEmit(Unit)
                                _events.tryEmit(SyncEvent.GroupInviteReceived(parsed.groupId, parsed.groupName))
                            }
                        }
                        groupManager != null && bodyStr.startsWith(GRP_KICK_PREFIX) -> {
                            val outcome = groupManager.handleKick(contact.id, bodyStr, owner.stadeId)
                            if (outcome != null) {
                                _events.tryEmit(
                                    if (outcome.wasSelf) SyncEvent.RemovedFromGroup(outcome.groupId, outcome.groupName)
                                    else SyncEvent.GroupMemberRemoved(outcome.groupId)
                                )
                            }
                        }
                        groupManager != null && bodyStr.startsWith(GRP_LEAVE_PREFIX) -> {
                            val groupId = groupManager.handleMemberLeft(contact.id, bodyStr)
                            if (groupId != null) _events.tryEmit(SyncEvent.GroupMemberRemoved(groupId))
                        }
                        stadiumManager != null && bodyStr.startsWith(STD_JOIN_PREFIX) -> {
                            val welcomeBody = stadiumManager.handleJoinRequest(contact.id, bodyStr)
                            if (welcomeBody != null) {
                                runCatching {
                                    val msgId = Encoding.toHex(crypto.randomBytes(16))
                                    val ts = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                    val sealed = ratchet.seal(owner, contact, welcomeBody.encodeToByteArray())
                                    val mp = MessagePayload(msgId, ts, sealed)
                                    val frame = json.encodeToString(MessagePayload.serializer(), mp).encodeToByteArray()
                                    outbox.enqueue(contact.id, msgId, frame)
                                    outboxSignal.tryEmit(Unit)
                                }
                            }
                        }
                        stadiumManager != null && bodyStr.startsWith(STD_WELCOME_PREFIX) -> {
                            stadiumManager.handleStadiumWelcome(owner.id, contact.id, bodyStr)
                        }
                        stadiumManager != null && bodyStr.startsWith(STD_LEAVE_PREFIX) -> {
                            val leftStadiumId = stadiumManager.handleLeaveRequest(contact.id, bodyStr)
                            if (leftStadiumId != null) {
                                val fresh = contacts.get(contact.id)
                                if (fresh != null && fresh.kind == 1 &&
                                    stadiumManager.stadiumsForContact(contact.id).isEmpty() &&
                                    stadiumManager.allStadiums(owner.id).none { it.creatorStadeId == contact.id } &&
                                    (groupManager?.groupsForContact(contact.id) ?: emptyList()).isEmpty() &&
                                    messages.lastMessage(contact.id) == null
                                ) {
                                    _events.tryEmit(SyncEvent.StadiumContactReleased(contact.id, forget = false))
                                }
                            }
                        }
                        stadiumManager != null && bodyStr.startsWith(STD_DELETE_PREFIX) -> {
                            val stadiumId = bodyStr.removePrefix(STD_DELETE_PREFIX)
                            val released = stadiumManager.handleStadiumDeletedByOwner(contact.id, stadiumId)
                            if (released) {
                                _events.tryEmit(SyncEvent.StadiumDeleted(stadiumId))
                                val fresh = contacts.get(contact.id)
                                if (fresh != null && fresh.kind == 1 &&
                                    stadiumManager.stadiumsForContact(contact.id).isEmpty() &&
                                    stadiumManager.allStadiums(owner.id).none { it.creatorStadeId == contact.id } &&
                                    (groupManager?.groupsForContact(contact.id) ?: emptyList()).isEmpty() &&
                                    messages.lastMessage(contact.id) == null
                                ) {
                                    _events.tryEmit(SyncEvent.StadiumContactReleased(contact.id, forget = false))
                                }
                            }
                        }
                        stadiumManager != null && bodyStr.startsWith(STD_INV_PREFIX) -> {
                            if ((contacts.get(contact.id)?.kind ?: 0) == 0) {
                                _events.tryEmit(SyncEvent.StadiumInviteReceived(bodyStr.removePrefix(STD_INV_PREFIX)))
                            }
                        }
                        stadiumManager != null && bodyStr.startsWith(STD_COUNT_REQUEST_PREFIX) -> {
                            val reply = stadiumManager.handleCountRequest(contact.id, bodyStr)
                            if (reply != null) {
                                runCatching {
                                    val msgId = Encoding.toHex(crypto.randomBytes(16))
                                    val ts = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                    val sealed = ratchet.seal(owner, contact, reply.encodeToByteArray())
                                    val mp = MessagePayload(msgId, ts, sealed)
                                    val frame = json.encodeToString(MessagePayload.serializer(), mp).encodeToByteArray()
                                    outbox.enqueue(contact.id, msgId, frame)
                                    outboxSignal.tryEmit(Unit)
                                }
                            }
                        }
                        stadiumManager != null && bodyStr.startsWith(STD_COUNT_PREFIX) -> {
                            stadiumManager.handleCountUpdate(contact.id, bodyStr)
                        }
                        stadiumManager != null && bodyStr.startsWith(STD_ABOUT_PREFIX) -> {
                            stadiumManager.handleAboutUpdate(contact.id, bodyStr)
                        }
                        stadiumManager != null && bodyStr.startsWith(STD_MSG_DELETE_PREFIX) -> {
                            val stripped = bodyStr.removePrefix(STD_MSG_DELETE_PREFIX)
                            val colonIdx = stripped.indexOf(':')
                            if (colonIdx >= 0) {
                                val stadiumId = stripped.substring(0, colonIdx)
                                val deletedMessageId = stripped.substring(colonIdx + 1)
                                val ok = stadiumManager.handleMessageDeletedByOwner(contact.id, stadiumId, deletedMessageId)
                                if (ok) _events.tryEmit(SyncEvent.StadiumMessageDeleted(stadiumId, deletedMessageId))
                            }
                        }
                        stadiumManager != null && bodyStr.startsWith(STD_MSG_PREFIX) -> {
                            val stripped = bodyStr.removePrefix(STD_MSG_PREFIX)
                            val colonIdx = stripped.indexOf(':')
                            if (colonIdx >= 0) {
                                val stadiumId = stripped.substring(0, colonIdx)
                                val rest = stripped.substring(colonIdx + 1)
                                val newlineIdx = rest.indexOf('\n')
                                if (newlineIdx >= 0) {
                                    val memberCount = rest.substring(0, newlineIdx).toLongOrNull()
                                    val stadiumBody = rest.substring(newlineIdx + 1)
                                    val stadium = stadiumManager.getStadium(stadiumId)
                                    if (memberCount != null && stadium != null && !stadium.isOwner && stadium.creatorStadeId == contact.id) {
                                        val ok = stadiumManager.handleIncomingBroadcast(stadiumId, payload.messageId, memberCount, stadiumBody, payload.timestamp)
                                        if (ok) _events.tryEmit(SyncEvent.StadiumMessageReceived(stadiumId))
                                    }
                                }
                            }
                        }
                        else -> {
                            if ((contacts.get(contact.id)?.kind ?: 0) == 0) {
                                val tag = parseVanishTag(bodyStr)
                                val now = Clock.System.now().toEpochMilliseconds()
                                if (tag == null || now < tag.deadlineAtMs) {
                                    val saved = messages.saveIncoming(
                                        payload.messageId, contact.id, tag?.innerBody ?: bodyStr, payload.timestamp, tag?.sessionId
                                    )
                                    if (saved != null) {
                                        _events.tryEmit(SyncEvent.MessageReceived(contact.id, payload.messageId))
                                        contacts.markSeen(contact.id, payload.timestamp)
                                    }
                                }
                            }
                        }
                    }

                    val ack = AckPayload(payload.messageId)
                    runCatching {
                        connection.send(FrameCodec.encode(SyncRecord(RecordType.ACK, json.encodeToString(AckPayload.serializer(), ack).encodeToByteArray())))
                    }
                }
                RecordType.ACK -> {
                    val ack = runCatching {
                        json.decodeFromString(AckPayload.serializer(), record.payload.decodeToString())
                    }.getOrNull() ?: return
                    messages.markDelivered(ack.messageId, contact.id)
                    outbox.removeForMessage(ack.messageId, contact.id)
                    runCatching { groupManager?.recordDelivery(ack.messageId, contact.id) }
                    val farewellForget = stadiumManager?.takeFarewellIfMatches(contact.id, ack.messageId)
                    if (farewellForget != null) {
                        _events.tryEmit(SyncEvent.StadiumContactReleased(contact.id, forget = farewellForget))
                    }
                }
                RecordType.PING -> { }
                RecordType.BYE -> { runCatching { connection.close() } }
                else -> { }
            }
        }
    }

    fun isConnected(contactId: String): Boolean = _connected.value.contains(contactId)

    fun clearVersionMismatch() {
        _versionMismatch.value = null
    }

    private fun clearVersionMismatchFor(contactId: String) {
        if (_versionMismatch.value?.peerId == contactId) _versionMismatch.value = null
    }

    private fun forgotten(): Set<String> {
        if (!forgottenLoaded) {
            forgottenIds = runCatching { contacts.forgottenIds() }.getOrDefault(emptySet())
            forgottenLoaded = true
        }
        return forgottenIds
    }

    suspend fun forgetContact(contactId: String) {
        sessionsLock.withLock {
            forgottenIds = forgotten() + contactId
            runCatching { contacts.setForgottenIds(forgottenIds) }
            sessions.remove(contactId)?.let { runCatching { it.cancel() } }
            _connected.value = sessions.keys.toSet()
        }
        runCatching { ratchet.forget(contactId) }
    }

    fun unforget(stadeId: String) {
        forgottenIds = forgotten() - stadeId
        runCatching { contacts.setForgottenIds(forgottenIds) }
    }

    fun markReAdd(stadeId: String) {
        pendingReAdds = pendingReAdds + stadeId
        unforget(stadeId)
    }

    fun clearReAdd(stadeId: String) {
        pendingReAdds = pendingReAdds - stadeId
    }

    suspend fun disconnectContact(contactId: String) {
        sessionsLock.withLock {
            sessions.remove(contactId)?.let { runCatching { it.cancel() } }
            _connected.value = sessions.keys.toSet()
        }
    }
}
