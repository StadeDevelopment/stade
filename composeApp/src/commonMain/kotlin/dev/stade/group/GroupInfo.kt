package dev.stade.group

import dev.stade.message.IMAGE_BODY_PREFIX
import dev.stade.message.decodeInboundAttachment
import dev.stade.message.MessageType
import dev.stade.message.isRetiredBody
import dev.stade.message.PAD_SOUND_BODY_PREFIX
import dev.stade.message.STICKER_BODY_PREFIX
import dev.stade.message.VIDEO_BODY_PREFIX
import dev.stade.message.VOICE_BODY_PREFIX
import dev.stade.message.parseReplyWrapper
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class GroupInfo(
    val id: String,
    val ownerId: String,
    val name: String,
    val inviteToken: String,
    val createdAt: Long,
    val memberIds: List<String> = emptyList(),
    val creatorStadeId: String = "",
    val muted: Boolean = false
)

data class GroupMessage(
    val id: String,
    val groupId: String,
    val senderId: String,
    val body: String,
    val timestamp: Long,
    val isOwn: Boolean,
    val isRead: Boolean
) {
    val replyToId: String?
        get() = parseReplyWrapper(body)?.first

    private val effectiveBody: String
        get() = parseReplyWrapper(body)?.second ?: body

    val displayBody: String
        get() = effectiveBody

    val type: MessageType
        get() = when {
            effectiveBody.startsWith(IMAGE_BODY_PREFIX) -> MessageType.IMAGE
            effectiveBody.startsWith(VOICE_BODY_PREFIX) -> MessageType.VOICE
            effectiveBody.startsWith(VIDEO_BODY_PREFIX) -> MessageType.VIDEO
            effectiveBody.startsWith(STICKER_BODY_PREFIX) -> MessageType.STICKER
            isRetiredBody(effectiveBody) -> MessageType.UNSUPPORTED
            else -> MessageType.TEXT
        }

    @OptIn(ExperimentalEncodingApi::class)
    fun imageBytes(): ByteArray? =
        if (type == MessageType.IMAGE)
            decodeInboundAttachment(effectiveBody.removePrefix(IMAGE_BODY_PREFIX).substringBefore('\n'))
        else null

    @OptIn(ExperimentalEncodingApi::class)
    fun videoBytes(): ByteArray? =
        if (type == MessageType.VIDEO)
            decodeInboundAttachment(effectiveBody.removePrefix(VIDEO_BODY_PREFIX).substringBefore('\n'))
        else null

    @OptIn(ExperimentalEncodingApi::class)
    fun stickerBytes(): ByteArray? =
        if (type == MessageType.STICKER)
            decodeInboundAttachment(effectiveBody.removePrefix(STICKER_BODY_PREFIX))
        else null

    val caption: String
        get() = when (type) {
            MessageType.IMAGE -> effectiveBody.removePrefix(IMAGE_BODY_PREFIX).substringAfter('\n', "")
            MessageType.VIDEO -> effectiveBody.removePrefix(VIDEO_BODY_PREFIX).substringAfter('\n', "")
            else -> ""
        }

    @OptIn(ExperimentalEncodingApi::class)
    fun voiceOpusBytes(): ByteArray? =
        if (type == MessageType.VOICE)
            runCatching {
                val raw = Base64.Default.decode(effectiveBody.removePrefix(VOICE_BODY_PREFIX))
                raw.copyOfRange(4, raw.size)
            }.getOrNull()
        else null

    @OptIn(ExperimentalEncodingApi::class)
    fun voiceDurationMs(): Int? =
        if (type == MessageType.VOICE)
            runCatching {
                val raw = Base64.Default.decode(effectiveBody.removePrefix(VOICE_BODY_PREFIX))
                ((raw[0].toInt() and 0xFF) shl 24) or ((raw[1].toInt() and 0xFF) shl 16) or
                    ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
            }.getOrNull()
        else null

}

data class GroupMemberEntry(
    val memberId: String,
    val nickname: String,
    val signingKey: ByteArray?,
    val mldsaKey: ByteArray?
) {
    val hasIdentity: Boolean get() = signingKey != null && mldsaKey != null

    override fun equals(other: Any?): Boolean =
        other is GroupMemberEntry && other.memberId == memberId && other.nickname == nickname &&
            other.signingKey.contentEquals(signingKey) && other.mldsaKey.contentEquals(mldsaKey)

    override fun hashCode(): Int = memberId.hashCode() * 31 + nickname.hashCode()
}

data class GroupFrame(
    val groupId: String,
    val senderId: String,
    val messageId: String,
    val timestamp: Long,
    val needsRelayTo: List<String>,
    val signature: ByteArray,
    val pqSignature: ByteArray?,
    val payload: String
)

data class RosterUpdate(val groupId: String, val changed: Boolean)

data class GroupInviteData(
    val groupId: String,
    val groupName: String,
    val inviteToken: String,
    val creatorStadeId: String
)

data class PendingJoinData(
    val groupId: String,
    val groupName: String,
    val inviteToken: String
)

data class KickOutcome(
    val groupId: String,
    val groupName: String,
    val wasSelf: Boolean
)

const val GRP_MSG_PREFIX = "\u0002GRP1:"
const val GRP_JOIN_PREFIX = "\u0002GRPJ:"
const val GRP_WELCOME_PREFIX = "\u0002GRPW:"
const val GRP_INV_PREFIX = "\u0002GRPI:"
const val GRP_KICK_PREFIX = "GRPK:"
const val GRP_LEAVE_PREFIX = "GRPL:"
const val GRP_RXN_PREFIX = "GRPR:"
const val GROUP_INVITE_PREFIX = "STADE-GRP:"

const val GROUP_PROTOCOL_VERSION = 2

const val GRP_FRAME_PREFIX = "\u0002GRP2:"
const val GRP_ROSTER_PREFIX = "\u0002GRPX:"

const val GRP_ACT_REACTION = "\u0002R:"
const val GRP_ACT_LEAVE = "\u0002L:"
const val GRP_ACT_KICK = "\u0002K:"
const val GRP_ACT_RECEIPT = "\u0002D:"

const val GRP_ROSTER_FIELD_SEP = '\u0001'
private const val GRP_SIG_CONTEXT = "stade-grp-v2\u0000"

const val GRP_NEEDS_SEP = ","

fun groupSigningMaterial(
    groupId: String,
    senderId: String,
    messageId: String,
    timestamp: Long,
    needsRelayTo: List<String>,
    payload: String
): ByteArray =
    (GRP_SIG_CONTEXT + groupId + "\u0000" + senderId + "\u0000" + messageId + "\u0000" +
        timestamp.toString() + "\u0000" + needsRelayTo.joinToString(GRP_NEEDS_SEP) + "\u0000" +
        payload).encodeToByteArray()

