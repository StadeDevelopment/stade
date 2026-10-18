package dev.stade.identity

const val MAX_DISPLAY_NAME_LEN = 64

fun sanitizeDisplayName(value: String, maxLength: Int = MAX_DISPLAY_NAME_LEN): String =
    value.asSequence()
        .filter { !isUnsafeDisplayChar(it) }
        .joinToString("")
        .trim()
        .take(maxLength)

fun sanitizeMultiline(value: String, maxLength: Int): String =
    value.asSequence()
        .filter { it == '\n' || !isUnsafeDisplayChar(it) }
        .joinToString("")
        .trim()
        .take(maxLength)

fun isUnsafeDisplayChar(c: Char): Boolean {
    val code = c.code
    return when {
        code < 0x20 -> true
        code == 0x7F -> true
        code in 0x80..0x9F -> true
        code in 0x200B..0x200F -> true
        code in 0x202A..0x202E -> true
        code in 0x2066..0x2069 -> true
        code == 0xFEFF -> true
        else -> false
    }
}
