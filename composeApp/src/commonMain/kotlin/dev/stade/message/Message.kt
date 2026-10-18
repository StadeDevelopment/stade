package dev.stade.message

import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

enum class MessageDirection { IN, OUT }
enum class MessageType { TEXT, IMAGE, VOICE, VIDEO, STICKER, UNSUPPORTED }
const val IMAGE_BODY_PREFIX = "STADE_IMG_V1:"
const val VOICE_BODY_PREFIX = "STADE_VOI_V1:"
const val VIDEO_BODY_PREFIX = "STADE_VID_V1:"
const val STICKER_BODY_PREFIX = "STADE_STK_V1:"
const val REPLY_BODY_PREFIX = "STADE_RPL_V1:"
const val REACTION_BODY_PREFIX = "STADE_RXN_V1:"
const val VANISH_START_PREFIX = "STADE_VST_V1:"
const val VANISH_CANCEL_PREFIX = "STADE_VCL_V1:"
const val VANISH_TAG_PREFIX = "STADE_VTG_V1:"
const val AVATAR_BODY_PREFIX = "STADE_AVT_V1:"
const val TYPING_BODY_PREFIX = "STADE_TYP_V1:"
const val PAD_SOUND_BODY_PREFIX = "STADE_PAD_V1:"
const val PROFILE_BODY_PREFIX = "STADE_PRF_V1:"
const val MAX_BIO_LEN = 200
const val MAX_STATUS_LEN = 16
const val MAX_PAD_NAME_LEN = 48

val RETIRED_BODY_PREFIXES = listOf("STADE_MEM_V1:", PAD_SOUND_BODY_PREFIX)

fun isRetiredBody(body: String): Boolean = RETIRED_BODY_PREFIXES.any { body.startsWith(it) }

const val MAX_ATTACHMENT_BYTES = 1800 * 1024
private const val MAX_ATTACHMENT_BASE64_CHARS = ((MAX_ATTACHMENT_BYTES + 2) / 3) * 4

@OptIn(ExperimentalEncodingApi::class)
fun decodeInboundAttachment(encoded: String): ByteArray? {
    if (encoded.length > MAX_ATTACHMENT_BASE64_CHARS) return null
    return runCatching { Base64.Default.decode(encoded) }
        .getOrNull()
        ?.takeIf { it.size <= MAX_ATTACHMENT_BYTES }
}

@Serializable
data class Message(
    val id: String,
    val contactId: String,
    val direction: MessageDirection,
    val body: String,
    val timestamp: Long,
    val delivered: Boolean,
    val read: Boolean,
    val vanishSessionId: String? = null
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

@OptIn(ExperimentalEncodingApi::class)
fun encodeImageBody(bytes: ByteArray, caption: String = ""): String =
    IMAGE_BODY_PREFIX + Base64.Default.encode(bytes) + if (caption.isNotEmpty()) "\n$caption" else ""

@OptIn(ExperimentalEncodingApi::class)
fun encodeVideoBody(bytes: ByteArray, caption: String = ""): String =
    VIDEO_BODY_PREFIX + Base64.Default.encode(bytes) + if (caption.isNotEmpty()) "\n$caption" else ""

@OptIn(ExperimentalEncodingApi::class)
fun encodeStickerBody(bytes: ByteArray): String =
    STICKER_BODY_PREFIX + Base64.Default.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
fun encodeVoiceBody(opusBytes: ByteArray, durationMs: Int): String {
    val header = byteArrayOf(
        (durationMs ushr 24).toByte(),
        (durationMs ushr 16).toByte(),
        (durationMs ushr 8).toByte(),
        durationMs.toByte()
    )
    return VOICE_BODY_PREFIX + Base64.Default.encode(header + opusBytes)
}

@OptIn(ExperimentalEncodingApi::class)
fun encodeAvatarBody(bytes: ByteArray?): String =
    AVATAR_BODY_PREFIX + (bytes?.let { Base64.Default.encode(it) } ?: "")

@OptIn(ExperimentalEncodingApi::class)
fun parseAvatarBody(body: String): ByteArray? {
    if (!body.startsWith(AVATAR_BODY_PREFIX)) return null
    val encoded = body.removePrefix(AVATAR_BODY_PREFIX)
    if (encoded.isEmpty()) return null
    return runCatching { Base64.Default.decode(encoded) }.getOrNull()
}

fun encodeTypingBody(typing: Boolean): String =
    TYPING_BODY_PREFIX + if (typing) "1" else "0"

fun parseTypingBody(body: String): Boolean? = when (body) {
    TYPING_BODY_PREFIX + "1" -> true
    TYPING_BODY_PREFIX + "0" -> false
    else -> null
}

fun encodeProfileBody(bio: String, status: String): String {
    val safeStatus = status.filter { it.isLetterOrDigit() }.take(MAX_STATUS_LEN)
    val safeBio = bio.asSequence().filter { it != '\n' && it != '\r' }.take(MAX_BIO_LEN).joinToString("")
    return PROFILE_BODY_PREFIX + safeStatus + "\n" + safeBio
}

fun parseProfileBody(body: String): Pair<String, String>? {
    if (!body.startsWith(PROFILE_BODY_PREFIX)) return null
    val rest = body.substring(PROFILE_BODY_PREFIX.length)
    val newline = rest.indexOf('\n')
    if (newline < 0) return null
    val status = rest.substring(0, newline).filter { it.isLetterOrDigit() }.take(MAX_STATUS_LEN)
    val bio = rest.substring(newline + 1).take(MAX_BIO_LEN)
    return status to bio
}

fun encodeReplyBody(replyToId: String, innerBody: String): String =
    REPLY_BODY_PREFIX + replyToId.length.toString() + ":" + replyToId + innerBody

fun parseReplyWrapper(body: String): Pair<String, String>? {
    if (!body.startsWith(REPLY_BODY_PREFIX)) return null
    val rest = body.substring(REPLY_BODY_PREFIX.length)
    val sep = rest.indexOf(':')
    if (sep < 0) return null
    val len = rest.substring(0, sep).toIntOrNull() ?: return null
    val afterSep = rest.substring(sep + 1)
    if (len < 0 || len > afterSep.length) return null
    val id = afterSep.substring(0, len)
    val inner = afterSep.substring(len)
    return id to inner
}

fun encodeReactionBody(targetMessageId: String, add: Boolean, emoji: String): String =
    REACTION_BODY_PREFIX + targetMessageId.length.toString() + ":" + targetMessageId +
        (if (add) "A" else "R") + emoji

data class ReactionWrapper(val targetMessageId: String, val add: Boolean, val emoji: String)

fun parseReactionWrapper(body: String): ReactionWrapper? {
    if (!body.startsWith(REACTION_BODY_PREFIX)) return null
    val rest = body.substring(REACTION_BODY_PREFIX.length)
    val sep = rest.indexOf(':')
    if (sep < 0) return null
    val len = rest.substring(0, sep).toIntOrNull() ?: return null
    val afterSep = rest.substring(sep + 1)
    if (len < 0 || len + 1 > afterSep.length) return null
    val id = afterSep.substring(0, len)
    val action = afterSep[len]
    if (action != 'A' && action != 'R') return null
    val emoji = afterSep.substring(len + 1)
    if (emoji.isEmpty()) return null
    return ReactionWrapper(id, action == 'A', emoji)
}

data class VanishStartWrapper(val sessionId: String, val startedAt: Long, val durationMs: Long)

fun encodeVanishStartBody(sessionId: String, startedAt: Long, durationMs: Long): String =
    VANISH_START_PREFIX + sessionId + ":" + startedAt + ":" + durationMs

fun parseVanishStartBody(body: String): VanishStartWrapper? {
    if (!body.startsWith(VANISH_START_PREFIX)) return null
    val parts = body.substring(VANISH_START_PREFIX.length).split(":")
    if (parts.size != 3) return null
    val startedAt = parts[1].toLongOrNull() ?: return null
    val durationMs = parts[2].toLongOrNull() ?: return null
    return VanishStartWrapper(parts[0], startedAt, durationMs)
}

fun encodeVanishCancelBody(sessionId: String): String = VANISH_CANCEL_PREFIX + sessionId

fun parseVanishCancelBody(body: String): String? =
    if (body.startsWith(VANISH_CANCEL_PREFIX)) body.substring(VANISH_CANCEL_PREFIX.length) else null

data class VanishTagWrapper(val sessionId: String, val deadlineAtMs: Long, val innerBody: String)

fun encodeVanishTag(sessionId: String, deadlineAtMs: Long, innerBody: String): String {
    val header = "$sessionId|$deadlineAtMs"
    return VANISH_TAG_PREFIX + header.length.toString() + ":" + header + innerBody
}

fun parseVanishTag(body: String): VanishTagWrapper? {
    if (!body.startsWith(VANISH_TAG_PREFIX)) return null
    val rest = body.substring(VANISH_TAG_PREFIX.length)
    val sep = rest.indexOf(':')
    if (sep < 0) return null
    val len = rest.substring(0, sep).toIntOrNull() ?: return null
    val afterSep = rest.substring(sep + 1)
    if (len < 0 || len > afterSep.length) return null
    val header = afterSep.substring(0, len)
    val inner = afterSep.substring(len)
    val pipeIdx = header.indexOf('|')
    if (pipeIdx < 0) return null
    val sessionId = header.substring(0, pipeIdx)
    val deadlineAtMs = header.substring(pipeIdx + 1).toLongOrNull() ?: return null
    return VanishTagWrapper(sessionId, deadlineAtMs, inner)
}
