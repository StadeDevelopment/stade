package dev.stade.contact

import dev.stade.db.StadeDb
import kotlinx.datetime.Clock

const val INVITE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

private const val INVITE_WINDOW_KV_KEY = "invite.window.until"

fun openInviteWindow(db: StadeDb, nowMs: Long = Clock.System.now().toEpochMilliseconds()) {
    runCatching {
        db.stadeDbQueries.putKv(
            INVITE_WINDOW_KV_KEY,
            (nowMs + INVITE_WINDOW_MS).toString().encodeToByteArray()
        )
    }
}

fun inviteWindowClosesAt(db: StadeDb): Long =
    runCatching { db.stadeDbQueries.getKv(INVITE_WINDOW_KV_KEY).executeAsOneOrNull() }
        .getOrNull()?.decodeToString()?.toLongOrNull() ?: 0L

fun isInviteWindowOpen(db: StadeDb, nowMs: Long = Clock.System.now().toEpochMilliseconds()): Boolean =
    nowMs < inviteWindowClosesAt(db)

fun closeInviteWindow(db: StadeDb) {
    runCatching { db.stadeDbQueries.putKv(INVITE_WINDOW_KV_KEY, "0".encodeToByteArray()) }
}
