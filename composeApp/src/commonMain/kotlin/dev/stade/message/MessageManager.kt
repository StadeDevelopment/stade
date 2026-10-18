package dev.stade.message

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.Encoding
import dev.stade.db.StadeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val PROCESSED_ENVELOPE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

data class SearchResult(
    val messageId: String,
    val chatId: String,
    val isGroup: Boolean,
    val title: String,
    val snippet: String,
    val timestamp: Long,
    val isStadium: Boolean = false
)

class MessageManager(private val db: StadeDb, private val crypto: CryptoApi) {

    fun searchInChat(contactId: String, query: String, limit: Long = 500): List<String> =
        db.stadeDbQueries.searchMessagesInChat(contactId, query, limit).executeAsList()

    fun filterInChat(contactId: String, filter: SearchFilter, limit: Long = 500): List<String> {
        val prefixes = filter.prefixes ?: return emptyList()
        return db.stadeDbQueries
            .typedMessagesInChat(contactId, prefixes.first, prefixes.second, limit)
            .executeAsList()
    }

    fun searchMessages(ownerId: String, query: String, limit: Long = 50): List<SearchResult> =
        db.stadeDbQueries.searchMessages(ownerId, query, limit).executeAsList().map {
            SearchResult(it.id, it.contactId, false, it.contactNickname, previewBody(it.body, ""), it.timestamp)
        }

    fun upsertReaction(messageId: String, fromId: String, emoji: String) {
        db.stadeDbQueries.upsertReaction(messageId, fromId, emoji)
    }

    fun deleteReaction(messageId: String, fromId: String) {
        db.stadeDbQueries.deleteReaction(messageId, fromId)
    }

    fun observeReactionsForMessage(messageId: String): Flow<List<dev.stade.db.MessageReaction>> =
        db.stadeDbQueries.selectReactionsForMessage(messageId)
            .asFlow()
            .mapToList(Dispatchers.Default)

    fun observeMessages(contactId: String): Flow<List<Message>> =
        db.stadeDbQueries.selectMessages(contactId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toMessage() } }

    fun observeLastMessage(contactId: String): Flow<Message?> =
        db.stadeDbQueries.selectLastMessagePreview(contactId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toMessage() }

    fun observeUnreadCount(contactId: String): Flow<Long> =
        db.stadeDbQueries.countUnread(contactId)
            .asFlow()
            .mapToOne(Dispatchers.Default)

    fun lastMessage(contactId: String): Message? =
        db.stadeDbQueries.selectLastMessagePreview(contactId).executeAsOneOrNull()?.toMessage()

    fun unreadCount(contactId: String): Long =
        db.stadeDbQueries.countUnread(contactId).executeAsOne()

    fun observeTotalUnread(): Flow<Long> =
        db.stadeDbQueries.countAllUnread()
            .asFlow()
            .mapToOne(Dispatchers.Default)

    fun totalUnread(): Long =
        db.stadeDbQueries.countAllUnread().executeAsOne()

    fun exists(messageId: String): Boolean =
        db.stadeDbQueries.messageExists(messageId).executeAsOne() > 0L

    fun isEnvelopeProcessed(messageId: String): Boolean =
        db.stadeDbQueries.envelopeProcessed(messageId).executeAsOne() > 0L

    fun markEnvelopeProcessed(messageId: String, contactId: String, timestamp: Long) {
        db.stadeDbQueries.markEnvelopeProcessed(messageId, contactId, timestamp)
        db.stadeDbQueries.pruneProcessedEnvelope(timestamp - PROCESSED_ENVELOPE_RETENTION_MS)
    }

    fun forgetEnvelope(messageId: String) {
        db.stadeDbQueries.forgetProcessedEnvelope(messageId)
    }

    fun newId(): String = Encoding.toHex(crypto.randomBytes(16))

    fun markRead(contactId: String) {
        db.stadeDbQueries.markRead(contactId)
    }

    fun markDelivered(messageId: String, contactId: String) {
        db.stadeDbQueries.markDelivered(messageId)
        db.stadeDbQueries.deleteOutboxForContactMessage(messageId, contactId)
    }

    fun saveOutgoing(contactId: String, body: String, timestamp: Long, vanishSessionId: String? = null): Message {
        val id = Encoding.toHex(crypto.randomBytes(16))
        val ts = monotonicTimestamp(contactId, timestamp)
        db.stadeDbQueries.insertMessage(id, contactId, "OUT", body, ts, 0, 1, vanishSessionId)
        return Message(id, contactId, MessageDirection.OUT, body, ts, false, true, vanishSessionId)
    }

    fun saveIncoming(messageId: String, contactId: String, body: String, timestamp: Long, vanishSessionId: String? = null): Message? {
        val exists = db.stadeDbQueries.messageExists(messageId).executeAsOne() > 0L
        if (exists) return null
        val ts = monotonicTimestamp(contactId, timestamp)
        db.stadeDbQueries.insertMessage(messageId, contactId, "IN", body, ts, 1, 0, vanishSessionId)
        return Message(messageId, contactId, MessageDirection.IN, body, ts, true, false, vanishSessionId)
    }

    private fun monotonicTimestamp(contactId: String, timestamp: Long): Long {
        val lastTs = db.stadeDbQueries.selectLastMessagePreview(contactId).executeAsOneOrNull()?.timestamp ?: return timestamp
        return maxOf(timestamp, lastTs + 1)
    }

    fun deleteMessages(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        db.stadeDbQueries.transaction {
            messageIds.forEach { id ->
                db.stadeDbQueries.deleteOutboxForMessage(id)
                db.stadeDbQueries.deleteMessageById(id)
                db.stadeDbQueries.deleteReactionsForMessage(id)
            }
        }
    }

    private fun dev.stade.db.Message.toMessage(): Message =
        Message(
            id, contactId,
            if (direction == "IN") MessageDirection.IN else MessageDirection.OUT,
            body, timestamp, delivered == 1L, read == 1L, vanishSessionId
        )

    private fun dev.stade.db.SelectLastMessagePreview.toMessage(): Message =
        Message(
            id, contactId,
            if (direction == "IN") MessageDirection.IN else MessageDirection.OUT,
            body, timestamp, delivered == 1L, read == 1L, vanishSessionId
        )
}


