package dev.stade.chat

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.stade.db.StadeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

private const val AUTO_UNARCHIVE_KEY = "arch.autoUnarchive"

const val GROUP_CHAT_KEY_PREFIX = "grp_"
const val STADIUM_CHAT_KEY_PREFIX = "std_"

fun archivedUnreadChats(
    archivedKeys: Set<String>,
    unreadContactIds: Set<String>,
    unreadGroupIds: Set<String>
): Int = archivedKeys.count { key ->
    when {
        key.startsWith(GROUP_CHAT_KEY_PREFIX) ->
            unreadGroupIds.contains(key.removePrefix(GROUP_CHAT_KEY_PREFIX))
        key.startsWith(STADIUM_CHAT_KEY_PREFIX) -> false
        else -> unreadContactIds.contains(key)
    }
}

class ArchivedChats(private val db: StadeDb) {
    private fun kvKey(ownerId: String, chatKey: String) = "arch.$ownerId.$chatKey"

    fun setArchived(ownerId: String, chatKey: String, archived: Boolean) {
        val key = kvKey(ownerId, chatKey)
        if (archived) {
            val ts = Clock.System.now().toEpochMilliseconds()
            db.stadeDbQueries.putKv(key, ts.toString().encodeToByteArray())
        } else {
            db.stadeDbQueries.deleteKv(key)
        }
    }

    fun observeArchived(ownerId: String): Flow<Set<String>> {
        val prefix = "arch.$ownerId."
        return db.stadeDbQueries.selectKvPrefixed("$prefix%")
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.mapTo(mutableSetOf()) { it.key.removePrefix(prefix) } }
    }

    fun observeUnreadChats(ownerId: String): Flow<Int> =
        combine(
            observeArchived(ownerId),
            db.stadeDbQueries.unreadContactIds().asFlow().mapToList(Dispatchers.Default),
            db.stadeDbQueries.unreadGroupIds().asFlow().mapToList(Dispatchers.Default)
        ) { keys, contactIds, groupIds ->
            archivedUnreadChats(keys, contactIds.toSet(), groupIds.toSet())
        }

    fun autoUnarchiveOnMessage(): Boolean =
        runCatching {
            db.stadeDbQueries.getKv(AUTO_UNARCHIVE_KEY).executeAsOneOrNull()
        }.getOrNull()?.decodeToString() == "1"

    fun observeAutoUnarchive(): Flow<Boolean> =
        db.stadeDbQueries.selectKvPrefixed(AUTO_UNARCHIVE_KEY)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.any { it.key == AUTO_UNARCHIVE_KEY && it.value_.decodeToString() == "1" } }

    fun setAutoUnarchiveOnMessage(enabled: Boolean) {
        runCatching {
            if (enabled) {
                db.stadeDbQueries.putKv(AUTO_UNARCHIVE_KEY, "1".encodeToByteArray())
            } else {
                db.stadeDbQueries.deleteKv(AUTO_UNARCHIVE_KEY)
            }
        }
    }

    fun archived(ownerId: String): Set<String> {
        val prefix = "arch.$ownerId."
        return db.stadeDbQueries.selectKvPrefixed("$prefix%")
            .executeAsList()
            .mapTo(mutableSetOf()) { it.key.removePrefix(prefix) }
    }
}
