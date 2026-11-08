package dev.stade.vanish

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.stade.db.StadeDb
import dev.stade.message.MessageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

object VanishDuration {
    const val THIRTY_MINUTES = 30L * 60 * 1000
    const val ONE_HOUR = 60L * 60 * 1000
    const val SIX_HOURS = 6L * 60 * 60 * 1000
    const val TWELVE_HOURS = 12L * 60 * 60 * 1000
    const val ONE_DAY = 24L * 60 * 60 * 1000
}

data class VanishSessionInfo(
    val sessionId: String,
    val contactId: String,
    val startedAt: Long,
    val durationMs: Long
) {
    val deadlineAtMs: Long get() = startedAt + durationMs
}

class VanishManager(
    private val db: StadeDb,
    private val messages: MessageManager
) {
    private val locks = mutableMapOf<String, Mutex>()
    private val locksMutex = Mutex()

    private suspend fun lockFor(contactId: String): Mutex =
        locksMutex.withLock { locks.getOrPut(contactId) { Mutex() } }

    suspend fun <T> withLock(contactId: String, block: suspend () -> T): T =
        lockFor(contactId).withLock { block() }

    fun observeCurrentSession(contactId: String): Flow<VanishSessionInfo?> =
        db.stadeDbQueries.selectVanishSessionsForContact(contactId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                val now = Clock.System.now().toEpochMilliseconds()
                rows.map { it.toInfo() }.firstOrNull { now < it.deadlineAtMs }
            }

    fun currentSessionLocked(contactId: String): VanishSessionInfo? {
        val now = Clock.System.now().toEpochMilliseconds()
        return db.stadeDbQueries.selectVanishSessionsForContact(contactId)
            .executeAsList()
            .map { it.toInfo() }
            .firstOrNull { now < it.deadlineAtMs }
    }

    fun startSessionLocked(contactId: String, durationMs: Long, now: Long): VanishSessionInfo {
        val sessionId = messages.newId()
        db.stadeDbQueries.insertVanishSession(sessionId, contactId, now, durationMs)
        return VanishSessionInfo(sessionId, contactId, now, durationMs)
    }

    fun cancelCurrentSessionLocked(contactId: String): VanishSessionInfo? {
        val session = currentSessionLocked(contactId) ?: return null
        deleteSessionMessagesAndRow(session)
        return session
    }

    fun cancelSessionLocked(contactId: String, sessionId: String) {
        val session = sessionsForContact(contactId).firstOrNull { it.sessionId == sessionId } ?: return
        deleteSessionMessagesAndRow(session)
    }

    fun adoptRemoteStartLocked(contactId: String, sessionId: String, startedAt: Long, durationMs: Long, now: Long) {
        if (now >= startedAt + durationMs) return
        if (durationMs > VanishDuration.ONE_DAY) return
        db.stadeDbQueries.insertVanishSession(sessionId, contactId, startedAt, durationMs)
    }

    fun ensureSweepableSessionLocked(
        contactId: String,
        sessionId: String,
        deadlineAtMs: Long,
        now: Long
    ): Boolean {
        if (now >= deadlineAtMs) return false
        val duration = deadlineAtMs - now
        if (duration > VanishDuration.ONE_DAY) return false
        if (sessionsForContact(contactId).any { it.sessionId == sessionId }) return true
        db.stadeDbQueries.insertVanishSession(sessionId, contactId, now, duration)
        return true
    }

    private fun sweepExpiredLocked(contactId: String, now: Long) {
        sessionsForContact(contactId).filter { now >= it.deadlineAtMs }.forEach { deleteSessionMessagesAndRow(it) }
    }

    suspend fun sweepIfExpired(contactId: String) {
        withLock(contactId) {
            sweepExpiredLocked(contactId, Clock.System.now().toEpochMilliseconds())
        }
    }

    suspend fun sweepAllActive() {
        val contactIds = db.stadeDbQueries.selectAllActiveVanishSessions().executeAsList()
            .map { it.contactId }.distinct()
        contactIds.forEach { sweepIfExpired(it) }
    }

    private fun sessionsForContact(contactId: String): List<VanishSessionInfo> =
        db.stadeDbQueries.selectVanishSessionsForContact(contactId).executeAsList().map { it.toInfo() }

    private fun deleteSessionMessagesAndRow(session: VanishSessionInfo) {
        val ids = db.stadeDbQueries.selectMessageIdsForVanishSession(session.sessionId).executeAsList()
        messages.deleteMessages(ids)
        db.stadeDbQueries.deleteVanishSession(session.sessionId)
    }

    private fun dev.stade.db.VanishSession.toInfo(): VanishSessionInfo =
        VanishSessionInfo(sessionId, contactId, startedAt, durationMs)
}
