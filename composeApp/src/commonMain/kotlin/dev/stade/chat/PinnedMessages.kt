package dev.stade.chat

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.stade.db.StadeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

data class PinnedRef(
    val messageId: String,
    val chatId: String,
    val pinnedAt: Long
)

class PinnedMessages(private val db: StadeDb) {

    private fun prefix(chatId: String) = "msgpin.$chatId."

    private fun kvKey(chatId: String, messageId: String) = prefix(chatId) + messageId

    fun setPinned(chatId: String, messageId: String, pinned: Boolean) {
        if (chatId.isBlank() || messageId.isBlank()) return
        val key = kvKey(chatId, messageId)
        if (pinned) {
            val ts = Clock.System.now().toEpochMilliseconds()
            db.stadeDbQueries.putKv(key, ts.toString().encodeToByteArray())
        } else {
            db.stadeDbQueries.deleteKv(key)
        }
    }

    fun observePinned(chatId: String): Flow<List<PinnedRef>> =
        db.stadeDbQueries.selectKvPrefixed(prefix(chatId) + "%")
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.mapNotNull { parse(chatId, it.key, it.value_) }.sortedByDescending { it.pinnedAt } }

    fun pinned(chatId: String): List<PinnedRef> =
        db.stadeDbQueries.selectKvPrefixed(prefix(chatId) + "%")
            .executeAsList()
            .mapNotNull { parse(chatId, it.key, it.value_) }
            .sortedByDescending { it.pinnedAt }

    fun unpinAll(chatId: String) {
        db.stadeDbQueries.selectKvPrefixed(prefix(chatId) + "%").executeAsList().forEach { row ->
            runCatching { db.stadeDbQueries.deleteKv(row.key) }
        }
    }

    private fun parse(chatId: String, key: String, value: ByteArray): PinnedRef? {
        val messageId = key.removePrefix(prefix(chatId))
        if (messageId.isBlank() || messageId == key) return null
        val ts = runCatching { value.decodeToString().toLongOrNull() }.getOrNull() ?: return null
        return PinnedRef(messageId = messageId, chatId = chatId, pinnedAt = ts)
    }
}
