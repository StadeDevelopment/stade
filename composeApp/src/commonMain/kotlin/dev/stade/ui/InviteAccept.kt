package dev.stade.ui

import dev.stade.AppContainer
import dev.stade.contact.Contact
import dev.stade.contact.InviteParseResult
import dev.stade.contact.InvitePayload
import dev.stade.contact.PROMOTE_TO_CONTACT_PREFIX
import dev.stade.crypto.Encoding
import dev.stade.identity.LocalIdentity
import dev.stade.stadium.PendingStadiumJoin
import dev.stade.transport.ExpectedPeer
import dev.stade.ui.i18n.AppStrings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock

private val STADE_ID_REGEX = Regex("^STADE-[0-9A-Za-z]{4}-[0-9A-Za-z]{4}-[0-9A-Za-z]{4}$")

fun InvitePayload.expectedPeer(): ExpectedPeer =
    ExpectedPeer(stadeId, signingPublicKey, mldsaPublicKey)

const val ACCEPT_INVITE_TIMEOUT_MS = 5 * 60_000L

fun AppContainer.promoteOrAlreadyAdded(owner: LocalIdentity, existing: Contact, strings: AppStrings): String {
    if (existing.kind == 0) return strings.alreadyAdded(existing.id)
    runCatching { contacts.setKind(existing.id, 0) }
    appScope.launch {
        runCatching {
            sync.queueOutgoing(
                owner, existing,
                Encoding.toHex(crypto.randomBytes(16)),
                PROMOTE_TO_CONTACT_PREFIX,
                Clock.System.now().toEpochMilliseconds()
            )
        }
    }
    return strings.contactAdded(existing.nickname)
}

fun inviteErrorText(result: InviteParseResult, strings: AppStrings): String? = when (result) {
    is InviteParseResult.Ok -> null
    is InviteParseResult.MissingPrefix -> strings.inviteMissingPrefix(result.firstChars)
    is InviteParseResult.TooShort -> strings.inviteTooShort(result.actual, result.expected)
    is InviteParseResult.TrailingBytes -> strings.inviteTrailingBytes(result.extra)
    InviteParseResult.BadMagic -> strings.inviteBadMagic
    is InviteParseResult.BadVersion -> strings.inviteBadVersion(result.version)
    is InviteParseResult.BadNickname -> strings.inviteBadNickname(result.length)
    is InviteParseResult.BadAddressBlob -> strings.inviteBadAddressBlob(result.length)
    InviteParseResult.EdVerifyFail -> strings.inviteEdVerifyFail
    InviteParseResult.MlDsaVerifyFail -> strings.inviteMlDsaVerifyFail
    is InviteParseResult.DecodeError -> strings.inviteDecodeError(result.cause)
}

sealed interface BeginAcceptResult {
    data class Error(val message: String) : BeginAcceptResult
    data class NoAddress(val payload: InvitePayload) : BeginAcceptResult
    data class Dialing(val payload: InvitePayload, val addressCount: Int, val lanOnly: Boolean) : BeginAcceptResult
}

fun AppContainer.beginAcceptInvite(
    owner: LocalIdentity,
    rawCode: String,
    alias: String,
    strings: AppStrings
): BeginAcceptResult {
    val trimmed = rawCode.trim()
    if (STADE_ID_REGEX.matches(trimmed.uppercase())) {
        return BeginAcceptResult.Error(strings.inviteCodeIsStadeId)
    }
    val parseResult = handshake.parseInviteDetailed(trimmed)
    val payload = (parseResult as? InviteParseResult.Ok)?.payload
        ?: return BeginAcceptResult.Error(inviteErrorText(parseResult, strings) ?: strings.invalidInvite)

    if (payload.signingPublicKey.contentEquals(owner.publicSigningKey)) {
        return BeginAcceptResult.Error(strings.selfInviteError)
    }
    val existing = contacts.findByStadeId(payload.stadeId)
    if (existing != null) {
        val a = alias.trim()
        if (a.isNotEmpty()) runCatching { contacts.rename(payload.stadeId, a) }
        val renamed = if (a.isNotEmpty()) existing.copy(nickname = a) else existing
        sync.markReAdd(payload.stadeId)
        val fresh = payload.addresses
        if (fresh.isNotEmpty()) {
            val nonLan = (existing.addresses + fresh).filter { !it.startsWith("lan://") }
            val currentLan = fresh.filter { it.startsWith("lan://") }
            val merged = (nonLan + currentLan).distinct()
            if (merged != existing.addresses) runCatching { contacts.setAddresses(existing.id, merged) }
            connections.queueDial(fresh, payload.expectedPeer())
        }
        return BeginAcceptResult.Error(promoteOrAlreadyAdded(owner, renamed, strings))
    }

    sync.markReAdd(payload.stadeId)
    val addrs = payload.addresses
    if (addrs.isEmpty()) return BeginAcceptResult.NoAddress(payload)

    val lanOnly = addrs.none { it.startsWith("tor://") }
    connections.queueDial(addrs, payload.expectedPeer())

    val a = alias.trim()
    val targetId = payload.stadeId
    appScope.launch {
        val added = withTimeoutOrNull(ACCEPT_INVITE_TIMEOUT_MS) {
            contacts.observeContacts(owner.id).first { list -> list.any { it.id == targetId } }
            true
        } ?: false
        if (added && a.isNotEmpty()) runCatching { contacts.rename(targetId, a) }
        if (!added) {
            connections.cancelPendingDial(addrs)
            sync.clearReAdd(targetId)
        }
    }
    return BeginAcceptResult.Dialing(payload, addrs.size, lanOnly)
}

sealed interface BeginAcceptStadiumResult {
    data class Error(val message: String) : BeginAcceptStadiumResult
    data class NoAddress(val stadiumName: String) : BeginAcceptStadiumResult
    data class Dialing(val stadiumName: String) : BeginAcceptStadiumResult
}

fun AppContainer.beginAcceptStadiumInvite(
    owner: LocalIdentity,
    rawCode: String,
    strings: AppStrings
): BeginAcceptStadiumResult {
    val trimmed = rawCode.trim()
    val split = stadiums.splitInviteLink(trimmed)
        ?: return BeginAcceptStadiumResult.Error(strings.notAStadiumInvite)
    val (handshakePart, stadiumDataPart) = split
    val stadiumData = stadiums.parseStadiumData(stadiumDataPart)
        ?: return BeginAcceptStadiumResult.Error(strings.notAStadiumInvite)
    val parseResult = handshake.parseInviteDetailed(handshakePart)
    val payload = (parseResult as? InviteParseResult.Ok)?.payload
        ?: return BeginAcceptStadiumResult.Error(inviteErrorText(parseResult, strings) ?: strings.invalidInvite)

    if (payload.signingPublicKey.contentEquals(owner.publicSigningKey)) {
        return BeginAcceptStadiumResult.Error(strings.selfInviteError)
    }

    sync.markReAdd(payload.stadeId)
    val addrs = payload.addresses
    val pending = PendingStadiumJoin(stadiumData.stadiumId, stadiumData.stadiumName, stadiumData.inviteToken)
    stadiums.storePendingJoin(payload.stadeId, pending)

    val existingContact = contacts.findByStadeId(payload.stadeId)
    if (existingContact != null) {
        appScope.launch { stadiumChat.sendJoinRequest(owner, existingContact.id, pending) }
    } else {
        if (addrs.isEmpty()) return BeginAcceptStadiumResult.NoAddress(stadiumData.stadiumName)
        connections.queueDial(addrs, payload.expectedPeer())
    }

    val stadiumId = stadiumData.stadiumId
    appScope.launch {
        val joined = withTimeoutOrNull(5 * 60_000L) {
            stadiums.observeStadiums(owner.id).first { list -> list.any { it.id == stadiumId } }
            true
        } ?: false
        if (!joined) {
            runCatching { stadiums.clearPendingJoin(payload.stadeId) }
            if (existingContact == null) connections.cancelPendingDial(addrs)
        }
    }
    return BeginAcceptStadiumResult.Dialing(stadiumData.stadiumName)
}
