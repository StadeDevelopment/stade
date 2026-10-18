package dev.stade.transport

import dev.stade.contact.ContactManager
import dev.stade.contact.Contact
import dev.stade.identity.LocalIdentity
import dev.stade.sync.SyncEngine
import dev.stade.ui.i18n.I18n
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeoutOrNull
import dev.stade.SHUTDOWN_GRACE_MS
import dev.stade.backgroundFailures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DialAttempt(
    val address: String,
    val timestamp: Long,
    val status: Status,
    val detail: String? = null
) {
    enum class Status { TRYING, CONNECT_OK, CONNECT_FAIL, HANDSHAKE_OK, HANDSHAKE_FAIL }
}

class ConnectionManager(
    private val registry: ConnectionRegistry,
    private val contacts: ContactManager,
    private val sync: SyncEngine,
    private val transportSettings: TransportSettings,
    private val isForeground: () -> Boolean = { true }
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundFailures)
    private val mutex = Mutex()
    private var ownerRef: LocalIdentity? = null
    private val tasks = mutableListOf<Job>()
    private val backoff = mutableMapOf<String, Long>()
    private val consecutiveFails = mutableMapOf<String, Int>()
    private val dialing = mutableMapOf<String, Long>()
    private val pendingDial = mutableSetOf<String>()
    private val pendingAttempts = mutableMapOf<String, Int>()
    private val pendingQueuedAt = mutableMapOf<String, Long>()
    private val pendingWake = Channel<Unit>(capacity = Channel.CONFLATED)
    private val dialerWake = Channel<Unit>(capacity = Channel.CONFLATED)

    private val _diagnostics = MutableStateFlow<Map<String, Map<String, DialAttempt>>>(emptyMap())
    val diagnostics: StateFlow<Map<String, Map<String, DialAttempt>>> = _diagnostics.asStateFlow()

    private val _pendingDials = MutableStateFlow<Map<String, DialAttempt>>(emptyMap())
    val pendingDials: StateFlow<Map<String, DialAttempt>> = _pendingDials.asStateFlow()

    private fun recordPending(attempt: DialAttempt) {
        _pendingDials.value = _pendingDials.value + (attempt.address to attempt)
    }

    private fun clearPending(addr: String) {
        _pendingDials.value = _pendingDials.value - addr
    }

    private fun recordAttempt(contactId: String, attempt: DialAttempt) {
        val cur = _diagnostics.value
        val perAddr = (cur[contactId] ?: emptyMap()).toMutableMap()
        perAddr[attempt.address] = attempt
        _diagnostics.value = cur + (contactId to perAddr)
    }

    private fun pruneAttempts(contactId: String, validAddresses: Set<String>) {
        val cur = _diagnostics.value
        val perAddr = cur[contactId] ?: return
        val kept = perAddr.filterKeys { it in validAddresses }
        if (kept.size != perAddr.size) {
            _diagnostics.value = cur + (contactId to kept)
        }
    }

    fun selfAddresses(): List<String> =
        registry.all().flatMap { runCatching { it.selfAddresses() }.getOrDefault(emptyList()) }
            .filter { it.isNotBlank() }
            .distinct()

    fun queueDial(addresses: List<String>) {
        val selfSet = runCatching { selfAddresses().toSet() }.getOrDefault(emptySet())
        synchronized(pendingDial) {
            for (a in addresses) {
                if (a.isNotBlank() && a !in selfSet) {
                    pendingDial.add(a)
                    pendingAttempts.remove(a)
                    pendingQueuedAt[a] = nowMs()
                    recordPending(DialAttempt(a, nowMs(), DialAttempt.Status.TRYING, I18n.current.dialQueued))
                }
            }
        }
        clearBackoffWithPrefix("pending|")
        pendingWake.trySend(Unit)
    }

    fun cancelPendingDial(addresses: List<String>) {
        for (a in addresses) consumePendingAddress(a)
    }

    private fun snapshotPendingAddresses(): List<String> =
        synchronized(pendingDial) { pendingDial.toList() }

    private fun consumePendingAddress(addr: String) {
        synchronized(pendingDial) {
            pendingDial.remove(addr)
            pendingAttempts.remove(addr)
            pendingQueuedAt.remove(addr)
        }
        clearPending(addr)
        removeBackoff("pending|$addr")
    }

    private fun nextPendingAttempt(addr: String): Int = synchronized(pendingDial) {
        val idx = (pendingAttempts[addr] ?: 0) + 1
        pendingAttempts[addr] = idx
        idx
    }

    private fun backoffUntil(key: String): Long = synchronized(backoff) { backoff[key] ?: 0L }

    private fun setBackoff(key: String, until: Long) {
        synchronized(backoff) { backoff[key] = until }
    }

    private fun removeBackoff(key: String) {
        synchronized(backoff) { backoff.remove(key) }
    }

    private fun clearBackoffWithPrefix(prefix: String) {
        synchronized(backoff) { backoff.keys.removeAll { it.startsWith(prefix) } }
    }

    private fun clearAllBackoff() {
        synchronized(backoff) { backoff.clear() }
    }

    private fun bumpFailCount(key: String): Int = synchronized(consecutiveFails) {
        val n = (consecutiveFails[key] ?: 0) + 1
        consecutiveFails[key] = n
        n
    }

    private fun clearFailCount(key: String) {
        synchronized(consecutiveFails) { consecutiveFails.remove(key) }
    }

    private fun clearFailCountWithPrefix(prefix: String) {
        synchronized(consecutiveFails) { consecutiveFails.keys.removeAll { it.startsWith(prefix) } }
    }

    private fun clearAllFailCounts() {
        synchronized(consecutiveFails) { consecutiveFails.clear() }
    }

    private fun nextContactBackoffMs(key: String): Long {
        if (isForeground()) return 30_000L
        val streak = bumpFailCount(key)
        return CONTACT_BACKOFF_STEPS_MS[minOf(streak - 1, CONTACT_BACKOFF_STEPS_MS.lastIndex)]
    }

    private fun isDialing(contactId: String): Boolean = synchronized(dialing) {
        val since = dialing[contactId] ?: return false
        if (nowMs() - since > DIAL_STALL_MS) {
            dialing.remove(contactId)
            false
        } else {
            true
        }
    }

    private fun markDialing(contactId: String) {
        synchronized(dialing) { dialing[contactId] = nowMs() }
    }

    private fun unmarkDialing(contactId: String) {
        synchronized(dialing) { dialing.remove(contactId) }
    }

    private fun clearDialing() {
        synchronized(dialing) { dialing.clear() }
    }

    suspend fun start(owner: LocalIdentity) = mutex.withLock {
        if (ownerRef?.id == owner.id) return@withLock
        if (ownerRef != null) stopInternal()
        ownerRef = owner
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundFailures)
        for (plugin in registry.all()) {
            if (!isEnabled(plugin.type)) continue
            tasks += scope.launch {
                runCatching {
                    plugin.start { connection -> sync.handleConnection(owner, connection) }
                }
            }
        }
        tasks += scope.launch { dialerLoop(owner) }
        tasks += scope.launch { pendingDialLoop(owner) }
        tasks += scope.launch {
            sync.events.collect { event ->
                when (event) {
                    is SyncEngine.SyncEvent.ContactDisconnected -> {
                        unmarkDialing(event.contactId)
                        scope.launch {
                            delay(1_000)
                            removeBackoff(event.contactId)
                            clearBackoffWithPrefix("${event.contactId}|")
                            clearFailCount(event.contactId)
                            clearFailCountWithPrefix("${event.contactId}|")
                            dialerWake.trySend(Unit)
                        }
                    }
                    is SyncEngine.SyncEvent.ContactConnected -> {
                        unmarkDialing(event.contactId)
                        clearFailCountWithPrefix("${event.contactId}|")
                        val siblingAddrs = runCatching { contacts.get(event.contactId)?.addresses }.getOrNull()
                        if (!siblingAddrs.isNullOrEmpty()) {
                            for (addr in siblingAddrs) consumePendingAddress(addr)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun onNetworkChanged() {
        clearAllBackoff()
        clearAllFailCounts()
        dialerWake.trySend(Unit)
        pendingWake.trySend(Unit)
    }

    suspend fun stop() = mutex.withLock { stopInternal() }

    suspend fun restart(type: TransportType) = mutex.withLock {
        val owner = ownerRef ?: return@withLock
        val plugin = registry.get(type) ?: return@withLock
        runCatching { plugin.stop() }
        if (!isEnabled(type)) return@withLock
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundFailures)
        tasks += scope.launch {
            runCatching {
                plugin.start { connection -> sync.handleConnection(owner, connection) }
            }
        }
    }

    suspend fun setTransportEnabled(type: TransportType, enabled: Boolean) = mutex.withLock {
        transportSettings.setEnabled(type, enabled)
        val plugin = registry.get(type) ?: return@withLock
        if (enabled) {
            val owner = ownerRef ?: return@withLock
            if (plugin.info.value.running) return@withLock
            if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundFailures)
            tasks += scope.launch {
                runCatching {
                    plugin.start { connection -> sync.handleConnection(owner, connection) }
                }
            }
        } else {
            runCatching { plugin.stop() }
        }
        dialerWake.trySend(Unit)
        pendingWake.trySend(Unit)
    }

    private fun isEnabled(type: TransportType): Boolean =
        runCatching { transportSettings.get(type).enabled }.getOrDefault(true)

    private suspend fun stopInternal() {
        ownerRef = null
        tasks.forEach { it.cancel() }
        for (plugin in registry.all()) runCatching { plugin.stop() }
        val running = tasks.toList()
        tasks.clear()
        runCatching { withTimeoutOrNull(SHUTDOWN_GRACE_MS) { running.joinAll() } }
        clearAllBackoff()
        clearAllFailCounts()
        clearDialing()
        synchronized(pendingDial) {
            pendingDial.clear()
            pendingAttempts.clear()
        }
        _pendingDials.value = emptyMap()
        val job = scope.coroutineContext[Job]
        scope.cancel()
        runCatching { withTimeoutOrNull(SHUTDOWN_GRACE_MS) { job?.join() } }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundFailures)
    }

    private suspend fun dialerLoop(owner: LocalIdentity) {
        while (scope.isActive) {
            val now = nowMs()
            for (contact in contacts.all().filter { it.ownerId == owner.id }) {
                if (sync.isConnected(contact.id)) continue
                if (isDialing(contact.id)) continue
                val attempted = tryDial(owner, contact, now)
                if (attempted && !sync.isConnected(contact.id)) {
                    setBackoff(contact.id, now + 10_000L)
                }
            }
            select<Unit> {
                dialerWake.onReceive { }
                onTimeout(5_000) { }
            }
        }
    }

    fun retryContact(contactId: String) {
        removeBackoff(contactId)
        clearBackoffWithPrefix("$contactId|")
        clearFailCount(contactId)
        clearFailCountWithPrefix("$contactId|")
        dialerWake.trySend(Unit)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun pendingDialLoop(owner: LocalIdentity) {
        while (scope.isActive) {
            val now = nowMs()
            tryDialPending(owner, now)
            select<Unit> {
                pendingWake.onReceive { }
                onTimeout(3_000) { }
            }
        }
    }

    private suspend fun tryDialPending(owner: LocalIdentity, now: Long) {
        val pending = snapshotPendingAddresses()
        if (pending.isEmpty()) return
        val knownAddrs: Set<String> = contacts.all()
            .filter { it.ownerId == owner.id }
            .flatMap { it.addresses }
            .toSet()
        for (addr in pending) {
            if (addr in knownAddrs) {
                consumePendingAddress(addr)
                continue
            }
            val queuedAt = synchronized(pendingDial) { pendingQueuedAt[addr] }
            if (queuedAt != null && now - queuedAt > MAX_PENDING_AGE_MS) {
                recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.CONNECT_FAIL, I18n.current.dialUnreachableTimeout))
                consumePendingAddress(addr)
                continue
            }
            val key = "pending|$addr"
            if (backoffUntil(key) > now) continue
            val plugin = pluginForAddress(addr)
            if (plugin == null) {
                recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.CONNECT_FAIL, I18n.current.dialTransportNotReady))
                setBackoff(key, nowMs() + 4_000L)
                continue
            }
            val pluginInfo = plugin.info.value
            if (!pluginInfo.running) {
                recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.TRYING, I18n.current.dialTransportStarting(pluginInfo.message ?: "")))
                setBackoff(key, nowMs() + 4_000L)
                continue
            }
            val attemptIdx = nextPendingAttempt(addr)
            val transportLabel = when (plugin.type) {
                TransportType.TOR -> "Tor"
                TransportType.LAN -> "LAN"
            }
            recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.TRYING, I18n.current.dialConnectingVia(transportLabel, attemptIdx)))
            setBackoff(key, nowMs() + 180_000L)
            scope.launch {
                val connResult = runCatching { plugin.connect(addr) }
                val conn = connResult.getOrNull()
                if (conn == null) {
                    val err = connResult.exceptionOrNull()?.message?.take(120) ?: I18n.current.dialUnreachableTimeout
                    recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.CONNECT_FAIL, I18n.current.dialConnectFailedRetry(err)))
                    setBackoff(key, nowMs() + nextPendingBackoffMs(attemptIdx))
                    noteDialOutcome(addr, connected = false)
                    pendingWake.trySend(Unit)
                    return@launch
                }
                noteDialOutcome(addr, connected = true)
                recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.CONNECT_OK, I18n.current.dialHandshaking))
                val sessionConnected = runCatching { sync.handleConnection(owner, conn, outbound = true) }.getOrDefault(false)
                if (sessionConnected) {
                    recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.HANDSHAKE_OK, I18n.current.dialConnectedOk))
                    consumePendingAddress(addr)
                } else {
                    recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.HANDSHAKE_FAIL, I18n.current.dialHandshakeFailedRetry))
                    setBackoff(key, nowMs() + nextPendingBackoffMs(attemptIdx))
                    pendingWake.trySend(Unit)
                }
            }
        }
    }

    private fun noteDialOutcome(address: String, connected: Boolean) {
        if (!address.startsWith("tor://") || connected) return
        val plugin = registry.get(TransportType.TOR) ?: return
        scope.launch {
            val healed = runCatching { plugin.refreshReachability() }.getOrDefault(false)
            if (healed) {
                clearAllBackoff()
                clearAllFailCounts()
                dialerWake.trySend(Unit)
                pendingWake.trySend(Unit)
            }
        }
    }

    private fun nextPendingBackoffMs(attemptIdx: Int): Long = when {
        attemptIdx <= 2 -> 3_000L
        attemptIdx <= 4 -> 6_000L
        attemptIdx <= 7 -> 10_000L
        attemptIdx <= 12 -> 20_000L
        else -> 40_000L
    }

    private suspend fun tryDial(owner: LocalIdentity, contact: Contact, now: Long): Boolean {
        var attempted = false
        val selfSet = runCatching { selfAddresses().toSet() }.getOrDefault(emptySet())
        pruneAttempts(contact.id, contact.addresses.toSet())
        for (addr in contact.addresses) {
            if (sync.isConnected(contact.id)) return attempted
            if (addr in selfSet) {
                recordAttempt(contact.id, DialAttempt(addr, now, DialAttempt.Status.CONNECT_FAIL, I18n.current.dialOwnStaleAddress))
                continue
            }
            val key = "${contact.id}|$addr"
            if (backoffUntil(key) > now) continue
            val plugin = pluginForAddress(addr)
            if (plugin == null) {
                recordAttempt(contact.id, DialAttempt(addr, now, DialAttempt.Status.CONNECT_FAIL, I18n.current.dialTransportClosed))
                continue
            }
            attempted = true
            markDialing(contact.id)
            recordAttempt(contact.id, DialAttempt(addr, now, DialAttempt.Status.TRYING))
            val conn = runCatching { plugin.connect(addr) }
            val connection = conn.getOrNull()
            if (connection == null) {
                unmarkDialing(contact.id)
                val err = conn.exceptionOrNull()?.message?.take(80) ?: I18n.current.dialUnreachableTimeout
                recordAttempt(contact.id, DialAttempt(addr, nowMs(), DialAttempt.Status.CONNECT_FAIL, err))
                setBackoff(key, nowMs() + nextContactBackoffMs(key))
                noteDialOutcome(addr, connected = false)
                continue
            }
            noteDialOutcome(addr, connected = true)
            recordAttempt(contact.id, DialAttempt(addr, nowMs(), DialAttempt.Status.CONNECT_OK, I18n.current.dialHandshaking))
            scope.launch {
                try {
                    val sessionConnected = sync.handleConnection(owner, connection, outbound = true)
                    if (sessionConnected || sync.isConnected(contact.id)) {
                        recordAttempt(contact.id, DialAttempt(addr, nowMs(), DialAttempt.Status.HANDSHAKE_OK, I18n.current.dialConnectedOk))
                        clearFailCount(key)
                    } else {
                        recordAttempt(contact.id, DialAttempt(addr, nowMs(), DialAttempt.Status.HANDSHAKE_FAIL, I18n.current.dialHandshakeFailed))
                        setBackoff(key, nowMs() + nextContactBackoffMs(key))
                    }
                } finally {
                    unmarkDialing(contact.id)
                }
            }
            return attempted
        }
        if (backoffUntil(contact.id) <= now) {
            for (plugin in registry.all()) {
                if (!isEnabled(plugin.type)) continue
                val discoverable = plugin as? DiscoverableTransport ?: continue
                for (addr in discoverable.discoveredPeers()) {
                    if (sync.isConnected(contact.id)) return attempted
                    if (isDialing(contact.id)) return attempted
                    val conn = runCatching { plugin.connect(addr) }.getOrNull() ?: continue
                    attempted = true
                    markDialing(contact.id)
                    scope.launch {
                        try {
                            sync.handleConnection(owner, conn, outbound = true)
                        } finally {
                            unmarkDialing(contact.id)
                        }
                    }
                }
            }
        }
        return attempted
    }

    private fun pluginForAddress(address: String): TransportPlugin? {
        val type = when {
            address.startsWith("tor://") -> TransportType.TOR
            address.startsWith("lan://") -> TransportType.LAN
            else -> return null
        }
        if (!isEnabled(type)) return null
        return registry.get(type)
    }

    private fun nowMs(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val MAX_PENDING_AGE_MS = 10 * 60_000L
        const val DIAL_STALL_MS = 150_000L
        val CONTACT_BACKOFF_STEPS_MS = longArrayOf(30_000L, 60_000L, 120_000L, 240_000L, 480_000L, 600_000L)
    }
}
