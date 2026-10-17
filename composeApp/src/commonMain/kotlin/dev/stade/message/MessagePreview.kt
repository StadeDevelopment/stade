package dev.stade.message

private const val PREVIEW_MAX = 120

fun previewBody(
    body: String,
    photoLabel: String,
    voiceLabel: String? = null,
    videoLabel: String? = null,
    stickerLabel: String? = null,
    unsupportedLabel: String? = null
): String {
    val effective = parseReplyWrapper(body)?.second ?: body
    if (unsupportedLabel != null && isRetiredBody(effective)) return unsupportedLabel
    if (effective.startsWith(IMAGE_BODY_PREFIX)) return photoLabel
    if (voiceLabel != null && effective.startsWith(VOICE_BODY_PREFIX)) return voiceLabel
    if (videoLabel != null && effective.startsWith(VIDEO_BODY_PREFIX)) return videoLabel
    if (stickerLabel != null && effective.startsWith(STICKER_BODY_PREFIX)) return stickerLabel
    val firstLine = effective.lineSequence().firstOrNull() ?: return ""
    return if (firstLine.length > PREVIEW_MAX) firstLine.substring(0, PREVIEW_MAX) else firstLine
}

fun previewWithSender(senderLabel: String?, preview: String): String =
    if (senderLabel.isNullOrBlank()) preview else "$senderLabel: $preview"

