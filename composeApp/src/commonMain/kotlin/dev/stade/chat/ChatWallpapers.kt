package dev.stade.chat

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.stade.db.StadeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

const val MAX_WALLPAPER_BYTES = 2 * 1024 * 1024

fun chatWallpaperKey(chatId: String) = "wallpaper.$chatId"

class ChatWallpapers(private val db: StadeDb) {

    private fun kvKey(chatId: String) = chatWallpaperKey(chatId)

    fun set(chatId: String, bytes: ByteArray?): Boolean {
        if (chatId.isBlank()) return false
        val key = kvKey(chatId)
        if (bytes == null || bytes.isEmpty()) {
            db.stadeDbQueries.deleteKv(key)
            return true
        }
        if (bytes.size > MAX_WALLPAPER_BYTES) return false
        db.stadeDbQueries.putKv(key, bytes)
        return true
    }

    fun get(chatId: String): ByteArray? =
        db.stadeDbQueries.getKv(kvKey(chatId)).executeAsOneOrNull()?.takeIf { it.isNotEmpty() }

    fun observe(chatId: String): Flow<ByteArray?> =
        db.stadeDbQueries.getKv(kvKey(chatId))
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.takeIf { bytes -> bytes.isNotEmpty() } }

    fun clearAll() {
        db.stadeDbQueries.selectKvPrefixed("wallpaper.%").executeAsList().forEach { row ->
            runCatching { db.stadeDbQueries.deleteKv(row.key) }
        }
    }
}
