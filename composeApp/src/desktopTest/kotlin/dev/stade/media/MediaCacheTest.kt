package dev.stade.media

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MediaCacheTest {

    @Test
    fun cacheNamesFollowContentNotAWeakHash() {
        val a = ByteArray(2048) { it.toByte() }
        val b = a.copyOf().also { it[2047] = (it[2047] + 1).toByte() }

        assertEquals(mediaCacheName(a), mediaCacheName(a.copyOf()), "the same bytes must reuse one file")
        assertNotEquals(
            mediaCacheName(a),
            mediaCacheName(b),
            "a one-byte difference must not share a cache file"
        )
        assertEquals(32, mediaCacheName(a).length, "the name must be a wide hash, not a 32-bit int")
    }

    @Test
    fun clearingRemovesEveryViewedMediaDirectory() {
        val tmp = File(System.getProperty("java.io.tmpdir"))
        val planted = MEDIA_CACHE_DIRS.map { name ->
            val dir = File(tmp, name).apply { mkdirs() }
            File(dir, "watched.mp4").apply { writeText("private video") }
        }
        planted.forEach { assertTrue(it.exists(), "fixture must exist first") }

        clearMediaCaches()

        planted.forEach {
            assertFalse(it.exists(), "a viewed video must not survive a lock or a wipe: ${it.absolutePath}")
        }
    }
}
