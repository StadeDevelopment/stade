package dev.stade

import dev.stade.db.DriverFactory
import dev.stade.db.StadeDb
import dev.stade.security.SessionTimeout
import dev.stade.security.Vault
import dev.stade.transport.TransportPlugin
import kotlinx.coroutines.runBlocking

class BootContext(
    val vault: Vault,
    val driverFactory: DriverFactory,
    val transportFactory: (StadeDb) -> List<TransportPlugin>,
    val onContainerCreated: (AppContainer) -> Unit = {}
) {
    private val lock = Any()
    private var active: AppContainer? = null

    @Volatile private var uiUnlocked = false
    @Volatile private var leftForegroundAtMillis = 0L

    fun markUnlocked() {
        uiUnlocked = true
        leftForegroundAtMillis = 0L
    }

    fun markLocked() {
        uiUnlocked = false
        leftForegroundAtMillis = 0L
    }

    fun noteLeftForeground() {
        if (uiUnlocked && leftForegroundAtMillis == 0L) {
            leftForegroundAtMillis = vault.nowMillis()
        }
    }

    fun resolveUnlocked(): Boolean {
        if (!uiUnlocked) return false
        val left = leftForegroundAtMillis
        if (left == 0L) return true
        val timeout = vault.sessionTimeoutSeconds()
        val expired = when (timeout) {
            SessionTimeout.NEVER -> false
            SessionTimeout.IMMEDIATE -> true
            else -> vault.nowMillis() - left >= timeout.toLong() * 1000L
        }
        if (expired) {
            markLocked()
            return false
        }
        leftForegroundAtMillis = 0L
        return true
    }

    suspend fun lockDown() {
        val container = synchronized(lock) { active }
        if (container != null) runCatching { container.close() }
        markLocked()
        runCatching { vault.flushAndClose() }
    }

    fun lockDownBlocking() {
        runBlocking { lockDown() }
    }

    fun activeContainer(): AppContainer? =
        synchronized(lock) { active?.takeIf { !it.isClosed } }

    fun buildContainer(): AppContainer = synchronized(lock) {
        active?.takeIf { !it.isClosed }?.let { return it }
        val retired = active
        val container = AppContainer(driverFactory, vault, transportFactory)
        if (retired != null) carryPendingIntents(retired, container)
        active = container
        onContainerCreated(container)
        container
    }

    private fun carryPendingIntents(from: AppContainer, to: AppContainer) {
        from.pendingInvite.value?.let { to.pendingInvite.value = it }
        from.pendingOpenChat.value?.let { to.pendingOpenChat.value = it }
        from.pendingOpenStadium.value?.let { to.pendingOpenStadium.value = it }
        from.pendingOpenGroup.value?.let { to.pendingOpenGroup.value = it }
        if (from.pendingGoHome.value) to.pendingGoHome.value = true
        if (from.pendingStickerImport.value.isNotEmpty()) {
            to.pendingStickerImport.value = from.pendingStickerImport.value
        }
    }
}
