package dev.stade.message

import dev.stade.db.StadeDb

private const val TYPING_SIGNAL_KV_KEY = "typingSignalEnabled"

fun getTypingSignalEnabled(db: StadeDb): Boolean =
    runCatching { db.stadeDbQueries.getKv(TYPING_SIGNAL_KV_KEY).executeAsOneOrNull() }
        .getOrNull()?.decodeToString() != "0"

fun setTypingSignalEnabled(db: StadeDb, enabled: Boolean) {
    db.stadeDbQueries.putKv(TYPING_SIGNAL_KV_KEY, (if (enabled) "1" else "0").encodeToByteArray())
}
