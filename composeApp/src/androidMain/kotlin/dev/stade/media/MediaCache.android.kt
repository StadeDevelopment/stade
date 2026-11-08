package dev.stade.media

import dev.stade.StadeApplication
import java.io.File
import java.security.MessageDigest

internal val MEDIA_CACHE_DIRS = listOf("videos_inline", "videos", "shared")

actual fun mediaCacheName(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .take(16)
        .joinToString("") { byte -> "%02x".format(byte) }

actual fun clearMediaCaches() {
    val context = runCatching { StadeApplication.instance.applicationContext }.getOrNull() ?: return
    MEDIA_CACHE_DIRS.forEach { name ->
        runCatching { File(context.cacheDir, name).deleteRecursively() }
    }
}
