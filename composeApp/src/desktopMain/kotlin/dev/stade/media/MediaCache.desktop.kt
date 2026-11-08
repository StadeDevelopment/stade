package dev.stade.media

import java.io.File
import java.security.MessageDigest

internal val MEDIA_CACHE_DIRS = listOf("stade_videos_inline", "stade_videos")

actual fun mediaCacheName(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .take(16)
        .joinToString("") { byte -> "%02x".format(byte) }

actual fun clearMediaCaches() {
    val tmp = System.getProperty("java.io.tmpdir") ?: return
    MEDIA_CACHE_DIRS.forEach { name ->
        runCatching { File(tmp, name).deleteRecursively() }
    }
}
