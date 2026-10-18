package dev.stade.message

enum class SearchFilter {
    All,
    Media,
    Voice,
    Stickers;

    val prefixes: Pair<String, String>?
        get() = when (this) {
            All -> null
            Media -> IMAGE_BODY_PREFIX to VIDEO_BODY_PREFIX
            Voice -> VOICE_BODY_PREFIX to VOICE_BODY_PREFIX
            Stickers -> STICKER_BODY_PREFIX to STICKER_BODY_PREFIX
        }
}
