package dev.stade.stadium

import dev.stade.message.IMAGE_BODY_PREFIX
import dev.stade.message.decodeInboundAttachment
import dev.stade.message.MessageType
import dev.stade.message.isRetiredBody
import dev.stade.message.PAD_SOUND_BODY_PREFIX
import dev.stade.message.STICKER_BODY_PREFIX
import dev.stade.message.VIDEO_BODY_PREFIX
import dev.stade.message.VOICE_BODY_PREFIX
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class StadiumInfo(
    val id: String,
    val ownerId: String,
    val name: String,
    val creatorStadeId: String,
    val isOwner: Boolean,
    val inviteToken: String,
    val memberCount: Long,
    val createdAt: Long,
    val muted: Boolean = false
)

data class StadiumMessage(
    val id: String,
    val stadiumId: String,
    val body: String,
    val timestamp: Long,
    val isOwn: Boolean
) {
    val displayBody: String
        get() = body

    val type: MessageType
        get() = when {
            body.startsWith(IMAGE_BODY_PREFIX) -> MessageType.IMAGE
            body.startsWith(VOICE_BODY_PREFIX) -> MessageType.VOICE
            body.startsWith(VIDEO_BODY_PREFIX) -> MessageType.VIDEO
            body.startsWith(STICKER_BODY_PREFIX) -> MessageType.STICKER
            isRetiredBody(body) -> MessageType.UNSUPPORTED
            else -> MessageType.TEXT
        }

    @OptIn(ExperimentalEncodingApi::class)
    fun imageBytes(): ByteArray? =
        if (type == MessageType.IMAGE)
            decodeInboundAttachment(body.removePrefix(IMAGE_BODY_PREFIX).substringBefore('\n'))
        else null

    @OptIn(ExperimentalEncodingApi::class)
    fun videoBytes(): ByteArray? =
        if (type == MessageType.VIDEO)
            decodeInboundAttachment(body.removePrefix(VIDEO_BODY_PREFIX).substringBefore('\n'))
        else null

    @OptIn(ExperimentalEncodingApi::class)
    fun stickerBytes(): ByteArray? =
        if (type == MessageType.STICKER)
            decodeInboundAttachment(body.removePrefix(STICKER_BODY_PREFIX))
        else null

    val caption: String
        get() = when (type) {
            MessageType.IMAGE -> body.removePrefix(IMAGE_BODY_PREFIX).substringAfter('\n', "")
            MessageType.VIDEO -> body.removePrefix(VIDEO_BODY_PREFIX).substringAfter('\n', "")
            else -> ""
        }

    @OptIn(ExperimentalEncodingApi::class)
    fun voiceOpusBytes(): ByteArray? =
        if (type == MessageType.VOICE)
            runCatching {
                val raw = Base64.Default.decode(body.removePrefix(VOICE_BODY_PREFIX))
                raw.copyOfRange(4, raw.size)
            }.getOrNull()
        else null

    @OptIn(ExperimentalEncodingApi::class)
    fun voiceDurationMs(): Int? =
        if (type == MessageType.VOICE)
            runCatching {
                val raw = Base64.Default.decode(body.removePrefix(VOICE_BODY_PREFIX))
                ((raw[0].toInt() and 0xFF) shl 24) or ((raw[1].toInt() and 0xFF) shl 16) or
                    ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
            }.getOrNull()
        else null

}

data class StadiumInviteData(
    val stadiumId: String,
    val stadiumName: String,
    val inviteToken: String,
    val creatorStadeId: String
)

data class PendingStadiumJoin(
    val stadiumId: String,
    val stadiumName: String,
    val inviteToken: String
)

const val STD_MSG_PREFIX = "STDM:"
const val STD_JOIN_PREFIX = "STDJ:"
const val STD_WELCOME_PREFIX = "STDW:"
const val STD_LEAVE_PREFIX = "STDL:"
const val STD_DELETE_PREFIX = "STDX:"
const val STD_MSG_DELETE_PREFIX = "STDMX:"
const val STD_INV_PREFIX = "STDI:"
const val STD_COUNT_PREFIX = "STDC:"
const val STD_COUNT_REQUEST_PREFIX = "STDQ:"
const val STADIUM_INVITE_SUFFIX_MARKER = "::STD::"
