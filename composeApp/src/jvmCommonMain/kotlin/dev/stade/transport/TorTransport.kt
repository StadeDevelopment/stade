package dev.stade.transport

import java.net.InetSocketAddress
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import dev.stade.backgroundFailures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import dev.stade.transport.tor.EmbeddedTorRuntime
import dev.stade.transport.tor.TorStatus
import dev.stade.transport.tor.parseBridgeConfig
import kotlinx.coroutines.flow.collectLatest

class TorTransport(
    private val defaultSocksHost: String = "127.0.0.1",
    private val defaultSocksPort: Int = 9050,
    private val configProvider: () -> String = { "" },
    private val embedded: EmbeddedTorRuntime? = null
) : BaseTransport(TransportType.TOR, "Tor") {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundFailures)
    private val selector = SelectorManager(Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var inboundOnion: String? = null
    @Volatile private var inboundPort: Int = 0
    @Volatile private var onionPublished: Boolean = true
    @Volatile private var inboundServer: ServerSocket? = null
    @Volatile private var socksHost: String = defaultSocksHost
    @Volatile private var socksPort: Int = defaultSocksPort
    @Volatile private var lastRepublishAt: Long = 0L
    @Volatile private var embeddedWatchersStarted: Boolean = false

    override suspend fun start(handler: suspend (Connection) -> Unit) {
        if (embedded != null) {
            startEmbedded(handler)
            return
        }
        mutex.withLock { startExternal(handler) }
    }

    private suspend fun startEmbedded(handler: suspend (Connection) -> Unit) {
        state.value = TransportInfo(type, "Tor", available = false, running = false, message = "starting Tor…")
        if (embeddedWatchersStarted) {
            bringUpEmbedded(handler)
            return
        }
        embeddedWatchersStarted = true
        scope.launch {
            embedded!!.statusFlow.collectLatest { st ->
                when (st) {
                    is TorStatus.Bootstrapping -> {
                        state.value = TransportInfo(
                            type,
                            "Tor",
                            available = false,
                            running = false,
                            message = "Tor boot ${st.percent}% · ${st.summary}",
                            bootstrapPercent = st.percent,
                            bootstrapPhase = st.summary
                        )
                    }
                    is TorStatus.Failed -> {
                        state.value = TransportInfo(type, "Tor", available = false, running = false, message = "failed to start: ${st.reason}", failed = true)
                    }
                    is TorStatus.Ready -> {
                        if (st.published && !onionPublished) {
                            onionPublished = true
                            mutex.withLock {
                                val msg = buildString {
                                    append("SOCKS5 ✓ ($socksHost:$socksPort)")
                                    if (inboundOnion != null) append(" · onion :$inboundPort ✓")
                                }
                                state.value = TransportInfo(type, "Tor", available = true, running = true, message = msg)
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
        scope.launch { runHealthMonitor(handler) }
        bringUpEmbedded(handler)
    }

    private suspend fun bringUpEmbedded(handler: suspend (Connection) -> Unit) {
        var boundPort = 0
        var preBoundServer: ServerSocket? = null
        var bindError: String? = null
        runCatching {
            val s = aSocket(selector).tcp().bind(hostname = "127.0.0.1", port = 0)
            preBoundServer = s
            boundPort = (s.localAddress as io.ktor.network.sockets.InetSocketAddress).port
        }.onFailure { err ->
            bindError = err.message ?: err::class.simpleName
        }

        val bridgeConfig = parseBridgeConfig(configProvider())
        val ready = runCatching { embedded!!.ensureReady(boundPort, bridgeConfig) }.getOrElse { err ->
            runCatching { preBoundServer?.close() }
            state.value = TransportInfo(type, "Tor", available = false, running = false, message = "failed to start: ${err.message ?: err::class.simpleName}", failed = true)
            return
        }
        mutex.withLock {
            socksHost = ready.socksHost
            socksPort = ready.socksPort
            inboundOnion = ready.onionHostname
            inboundPort = ready.onionVirtualPort
            onionPublished = ready.onionPublished ||
                (embedded?.statusFlow?.value as? TorStatus.Ready)?.published == true
            val listenError: String? = bindError
            if (preBoundServer != null && bindError == null) {
                runCatching { inboundServer?.close() }
                inboundServer = preBoundServer
                scope.launch { runAccept(preBoundServer!!, handler) }
            } else {
                inboundOnion = null
                inboundPort = 0
            }
            val msg = buildString {
                append("SOCKS5 ✓ ($socksHost:$socksPort)")
                if (inboundOnion != null) {
                    if (onionPublished) append(" · onion :$inboundPort ✓")
                    else append(" · onion :$inboundPort (publishing…)")
                } else if (listenError != null) {
                    append(" · onion NOT LISTENING ($listenError)")
                }
            }
            state.value = TransportInfo(type, "Tor", available = true, running = true, message = msg)
        }
    }

    private suspend fun runHealthMonitor(handler: suspend (Connection) -> Unit) {
        var lastRestartAt = 0L
        while (scope.isActive) {
            kotlinx.coroutines.delay(10_000)
            val emb = embedded ?: return
            if (!state.value.running) continue
            if (emb.isAlive()) continue
            val now = System.currentTimeMillis()
            if (now - lastRestartAt < 30_000) continue
            lastRestartAt = now
            state.value = state.value.copy(running = false, available = false, message = "Tor process exited — restarting…")
            runCatching { inboundServer?.close() }
            inboundServer = null
            runCatching { emb.invalidate() }
            runCatching { bringUpEmbedded(handler) }
        }
    }

    private suspend fun startExternal(handler: suspend (Connection) -> Unit) {
        val cfg = parseConfig(configProvider())
        val cfgHost = cfg["socksHost"]?.takeIf { it.isNotBlank() } ?: defaultSocksHost
        val cfgPort = cfg["socksPort"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: defaultSocksPort
        val probeOrder = buildList {
            add(cfgHost to cfgPort)
            if (cfgHost == "127.0.0.1") {
                if (cfgPort != 9050) add("127.0.0.1" to 9050)
                if (cfgPort != 9150) add("127.0.0.1" to 9150)
            }
        }
        var socksOk = false
        var autoSwitched = false
        for ((h, p) in probeOrder) {
            if (probeSocks(h, p)) {
                socksHost = h
                socksPort = p
                socksOk = true
                autoSwitched = (h != cfgHost || p != cfgPort)
                break
            }
        }
        if (!socksOk) {
            socksHost = cfgHost
            socksPort = cfgPort
        }
        inboundOnion = cfg["onion"]?.takeIf { it.isNotBlank() }
        val publicPort = cfg["port"]?.toIntOrNull() ?: 0
        val listenPort = cfg["listenPort"]?.toIntOrNull()?.takeIf { it > 0 } ?: publicPort
        inboundPort = publicPort
        val listenHost = cfg["listenHost"] ?: "127.0.0.1"

        var listening = false
        var listenError: String? = null
        if (listenPort > 0) {
            runCatching {
                val s = aSocket(selector).tcp().bind(hostname = listenHost, port = listenPort)
                inboundServer = s
                scope.launch { runAccept(s, handler) }
                listening = true
            }.onFailure { err ->
                inboundOnion = null
                inboundPort = 0
                val raw = err.message ?: err::class.simpleName ?: "bind error"
                listenError = if (err is java.net.BindException) {
                    "$listenHost:$listenPort already in use — set a different listenPort in torrc"
                } else raw
            }
        }

        val msg = buildString {
            if (socksOk) {
                append("SOCKS5 ✓ ($socksHost:$socksPort)")
                if (autoSwitched) append(" [auto]")
            } else {
                append("SOCKS5 unavailable — Tor/Orbot not running (probed 9050 and 9150)")
            }
            when {
                listening -> {
                    append(" · onion :$inboundPort ✓")
                    if (listenPort != inboundPort) append(" (local :$listenPort)")
                }
                listenError != null -> append(" · onion NOT LISTENING ($listenError)")
                cfg["onion"].isNullOrBlank() -> append(" · onion address not entered")
            }
        }
        val anyUp = socksOk || listening
        state.value = TransportInfo(
            type, "Tor",
            available = anyUp,
            running = anyUp,
            message = msg
        )
    }

    override suspend fun stop() = mutex.withLock {
        runCatching { inboundServer?.close() }
        inboundServer = null
        state.value = state.value.copy(running = false)
    }

    override suspend fun reload() {
        embedded?.invalidate()
    }

    override suspend fun refreshReachability(): Boolean {
        val emb = embedded ?: return false
        val now = System.currentTimeMillis()
        if (now - lastRepublishAt < 300_000) return false
        lastRepublishAt = now
        val ok = runCatching { emb.republishOnion() }.getOrDefault(false)
        val current = (emb.statusFlow.value as? TorStatus.Ready)?.onion
        if (current != null) inboundOnion = current
        onionPublished = ok
        state.value = state.value.copy(
            message = buildString {
                append("SOCKS5 ✓ ($socksHost:$socksPort)")
                if (inboundOnion != null) {
                    if (ok) append(" · onion :$inboundPort ✓")
                    else append(" · onion :$inboundPort (republishing…)")
                }
            }
        )
        return ok
    }

    override suspend fun connect(address: String): Connection? {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            try {
                val result = connectOnce(address)
                if (result != null) return result
                lastError = IllegalStateException("Tor connect timed out")
            } catch (t: Throwable) {
                lastError = t
            }
            if (attempt < 2) kotlinx.coroutines.delay(1_500L * (attempt + 1))
        }
        throw lastError ?: IllegalStateException("Tor connect failed")
    }

    private suspend fun connectOnce(address: String): Connection? {
        val onion = address.removePrefix("tor://")
        val parts = onion.split(":", limit = 2)
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 80
        val sHost = socksHost
        val sPort = socksPort
        if (sPort <= 0) throw IllegalStateException("Tor SOCKS not ready yet (port=$sPort)")
        return withTimeoutOrNull(60_000) {
            var socket: io.ktor.network.sockets.Socket? = null
            try {
                socket = try {
                    aSocket(selector).tcp().connect(hostname = sHost, port = sPort)
                } catch (t: Throwable) {
                    throw IllegalStateException("Tor SOCKS unreachable at $sHost:$sPort (${t.message ?: t::class.simpleName})", t)
                }
                val reader = socket.openReadChannel()
                val writer = socket.openWriteChannel(autoFlush = true)

                val greeting = byteArrayOf(0x05, 0x01, 0x00)
                writer.writeFully(greeting, 0, greeting.size)
                val greetReply = ByteArray(2)
                reader.readFully(greetReply)
                if (greetReply[0] != 0x05.toByte() || greetReply[1] != 0x00.toByte()) {
                    throw IllegalStateException("SOCKS5 greeting rejected (is the Tor service running?)")
                }

                val hostBytes = host.toByteArray(Charsets.US_ASCII)
                val req = ByteArray(7 + hostBytes.size)
                req[0] = 0x05; req[1] = 0x01; req[2] = 0x00; req[3] = 0x03
                req[4] = hostBytes.size.toByte()
                hostBytes.copyInto(req, 5)
                req[5 + hostBytes.size] = ((port ushr 8) and 0xff).toByte()
                req[6 + hostBytes.size] = (port and 0xff).toByte()
                writer.writeFully(req, 0, req.size)

                val head = ByteArray(4)
                reader.readFully(head)
                val rep = head[1].toInt() and 0xff
                if (rep != 0x00) {
                    throw IllegalStateException("SOCKS5 error: ${socksReplyMessage(rep)}")
                }
                val rest = when (head[3].toInt() and 0xff) {
                    0x01 -> 4 + 2
                    0x03 -> {
                        val nb = ByteArray(1)
                        reader.readFully(nb)
                        (nb[0].toInt() and 0xff) + 2
                    }
                    0x04 -> 16 + 2
                    else -> 0
                }
                if (rest > 0) reader.readFully(ByteArray(rest))
                val ok = TcpConnection(socket, reader, writer, "tor://$host:$port")
                socket = null
                ok
            } finally {
                socket?.let { runCatching { it.close() } }
            }
        }
    }

    private fun socksReplyMessage(code: Int): String = when (code) {
        0x01 -> "general SOCKS server failure"
        0x02 -> "connection not allowed by ruleset"
        0x03 -> "network unreachable"
        0x04 -> "host unreachable (onion descriptor missing / not propagated)"
        0x05 -> "connection refused (the peer is not listening on .onion)"
        0x06 -> "TTL expired"
        0x07 -> "command not supported"
        0x08 -> "address type not supported"
        else -> "code=$code"
    }

    override fun selfAddress(): String? {
        val o = inboundOnion ?: return null
        if (embedded != null && !onionPublished) return null
        return if (inboundPort > 0) "tor://$o:$inboundPort" else null
    }

    fun socksProxyAddress(): Pair<String, Int>? {
        val h = socksHost
        val p = socksPort
        return if (p > 0 && state.value.running) h to p else null
    }

    private suspend fun runAccept(server: ServerSocket, handler: suspend (Connection) -> Unit) {
        while (scope.isActive) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            scope.launch {
                val conn = TcpConnection(socket, "tor://inbound")
                handler(conn)
            }
        }
    }

    private fun parseConfig(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && '=' in it }
            .map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            .toMap()
    }

    private suspend fun checkSocks(): Boolean = probeSocks(socksHost, socksPort)

    private suspend fun probeSocks(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            java.net.Socket().use { it.connect(InetSocketAddress(host, port), 1500) }
            true
        }.getOrDefault(false)
    }
}
