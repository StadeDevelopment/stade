package dev.stade.transport

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import dev.stade.backgroundFailures
import dev.stade.runCancellable
import dev.stade.sync.MAX_WIRE_FRAME_BYTES
import dev.stade.ui.i18n.I18n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

class LanTransport(
    private val nodeId: String,
    private val tcpPort: Int = 5901,
    private val discoveryPort: Int = 5902
) : BaseTransport(TransportType.LAN, "LAN"), DiscoverableTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundFailures)
    private val selector = SelectorManager(Dispatchers.IO)
    private var server: ServerSocket? = null
    @Volatile private var actualPort: Int = tcpPort
    private val broadcastId = RotatingNodeId(nodeId)
    private val discovery = DiscoveryService({ broadcastId.current() }, discoveryPort) { actualPort }
    private val inboundLimiter = InboundAcceptLimiter()
    private val mutex = Mutex()

    override suspend fun start(handler: suspend (Connection) -> Unit) = mutex.withLock {
        if (state.value.running) return@withLock
        val lanIps = allLocalIpv4()
        var lastErr: Throwable? = null
        for (candidate in tcpPort..(tcpPort + 9)) {
            try {
                val s = aSocket(selector).tcp().bind(hostname = "0.0.0.0", port = candidate) { reuseAddress = true }
                server = s
                actualPort = candidate
                val msg = buildString {
                    if (lanIps.isEmpty()) append("0.0.0.0:$candidate")
                    else append(lanIps.joinToString(", ") { "$it:$candidate" })
                    if (candidate != tcpPort) append(I18n.current.lanPortFallbackSuffix(tcpPort))
                }
                state.value = TransportInfo(
                    type, "LAN",
                    available = true, running = true,
                    message = msg
                )
                scope.launch { runAccept(s, handler) }
                scope.launch { discovery.start() }
                return@withLock
            } catch (e: Throwable) {
                lastErr = e
                continue
            }
        }
        state.value = TransportInfo(type, "LAN", available = false, running = false,
            message = I18n.current.lanNoFreePort(tcpPort, tcpPort + 9, lastErr?.message ?: ""))
    }

    override suspend fun stop() = mutex.withLock {
        runCatching { server?.close() }
        server = null
        discovery.stop()
        state.value = state.value.copy(running = false)
    }

    override suspend fun connect(address: String): Connection? {
        val (host, portStr) = address.removePrefix("lan://").split(":", limit = 2).let {
            it[0] to (it.getOrNull(1)?.toIntOrNull() ?: tcpPort)
        }
        return kotlinx.coroutines.withTimeoutOrNull(5_000) {
            runCatching {
                val socket = aSocket(selector).tcp().connect(hostname = host, port = portStr) {
                    noDelay = true
                    keepAlive = true
                }
                EncryptedLinkConnection(TcpConnection(socket, "lan://$host:$portStr")) as Connection
            }.getOrNull()
        }
    }

    override fun selfAddress(): String? {
        val ip = primaryIpv4() ?: return null
        return "lan://$ip:$actualPort"
    }

    override fun selfAddresses(): List<String> =
        allLocalIpv4().map { "lan://$it:$actualPort" }

    override fun discoveredPeers(): List<String> = discovery.snapshot()

    private suspend fun runAccept(server: ServerSocket, handler: suspend (Connection) -> Unit) {
        while (scope.isActive) {
            val socket = runCancellable { server.accept() }.getOrNull() ?: break
            if (!inboundLimiter.tryAcquire()) {
                runCatching { socket.close() }
                continue
            }
            scope.launch {
                try {
                    val conn = EncryptedLinkConnection(TcpConnection(socket, "lan://${socket.remoteAddress}"))
                    handler(conn)
                } finally {
                    inboundLimiter.release()
                }
            }
        }
    }

    private fun primaryIpv4(): String? = allLocalIpv4().firstOrNull()

    private fun allLocalIpv4(): List<String> = runCatching {
        val out = mutableListOf<String>()
        val ifs = NetworkInterface.getNetworkInterfaces()
        for (ni in ifs) {
            if (!ni.isUp || ni.isLoopback || ni.isVirtual) continue
            for (addr in ni.inetAddresses) {
                val host = addr.hostAddress ?: continue
                if (addr.isLoopbackAddress) continue
                if (host.contains(":")) continue
                if (!addr.isSiteLocalAddress &&
                    !host.startsWith("10.") &&
                    !host.startsWith("172.") &&
                    !host.startsWith("192.168.")) continue
                out += host
            }
        }
        out
    }.getOrDefault(emptyList())
}

private const val READ_CHUNK_BYTES = 64 * 1024

internal class TcpConnection : Connection {
    private val socket: Socket
    private val reader: ByteReadChannel
    private val writer: ByteWriteChannel
    private val writeMutex = Mutex()
    override val remoteAddress: String

    constructor(socket: Socket, remoteAddress: String) {
        this.socket = socket
        this.reader = socket.openReadChannel()
        this.writer = socket.openWriteChannel(autoFlush = true)
        this.remoteAddress = remoteAddress
    }

    constructor(socket: Socket, reader: ByteReadChannel, writer: ByteWriteChannel, remoteAddress: String) {
        this.socket = socket
        this.reader = reader
        this.writer = writer
        this.remoteAddress = remoteAddress
    }

    override suspend fun send(frame: ByteArray): Unit = writeMutex.withLock {
        val len = frame.size
        val header = ByteArray(4)
        header[0] = ((len ushr 24) and 0xff).toByte()
        header[1] = ((len ushr 16) and 0xff).toByte()
        header[2] = ((len ushr 8) and 0xff).toByte()
        header[3] = (len and 0xff).toByte()
        writer.writeFully(header, 0, 4)
        var written = 0
        while (written < len) {
            val until = minOf(written + READ_CHUNK_BYTES, len)
            writer.writeFully(frame, written, until)
            written = until
            progressAt = System.currentTimeMillis()
        }
        writer.flush()
        progressAt = System.currentTimeMillis()
    }

    @Volatile private var progressAt = System.currentTimeMillis()

    override val lastProgressAtMs: Long get() = progressAt

    override suspend fun receive(): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val header = ByteArray(4)
            reader.readFully(header)
            progressAt = System.currentTimeMillis()
            val len = ((header[0].toInt() and 0xff) shl 24) or
                ((header[1].toInt() and 0xff) shl 16) or
                ((header[2].toInt() and 0xff) shl 8) or
                (header[3].toInt() and 0xff)
            if (len <= 0 || len > MAX_WIRE_FRAME_BYTES) return@runCatching null
            val payload = ByteArray(len)
            var filled = 0
            while (filled < len) {
                val until = minOf(filled + READ_CHUNK_BYTES, len)
                reader.readFully(payload, filled, until)
                filled = until
                progressAt = System.currentTimeMillis()
            }
            payload
        }.getOrNull()
    }

    override suspend fun close() {
        runCatching { socket.close() }
    }
}

private class DiscoveryService(
    private val nodeIdProvider: () -> String,
    private val port: Int,
    private val tcpPortProvider: () -> Int
) {
    private val peers = mutableMapOf<String, Long>()
    private val mutex = Mutex()
    private var rxJob: Job? = null
    private var txJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundFailures)
    private var receiver: DatagramSocket? = null
    private var sender: DatagramSocket? = null

    suspend fun start() {
        if (rxJob != null) return
        runCatching { DatagramSocket(port) }.getOrNull()?.let { sock ->
            receiver = sock.also { it.broadcast = true }
            rxJob = scope.launch { receiveLoop(sock) }
        }
        runCatching { DatagramSocket() }.getOrNull()?.let { sock ->
            sender = sock.also { it.broadcast = true }
            txJob = scope.launch { broadcastLoop(sock) }
        }
    }

    fun stop() {
        rxJob?.cancel(); txJob?.cancel()
        rxJob = null; txJob = null
        runCatching { receiver?.close() }
        runCatching { sender?.close() }
    }

    fun snapshot(): List<String> = synchronized(peers) {
        val now = System.currentTimeMillis()
        peers.entries.removeAll { now - it.value > 60_000 }
        peers.keys.toList()
    }

    private suspend fun receiveLoop(sock: DatagramSocket) {
        val buffer = ByteArray(512)
        while (scope.isActive) {
            val packet = DatagramPacket(buffer, buffer.size)
            val received = runCatching { sock.receive(packet) }.isSuccess
            if (!received) {
                if (sock.isClosed || !scope.isActive) return
                kotlinx.coroutines.delay(1_000)
                continue
            }
            val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
            val parts = text.split('|')
            if (parts.size < 3 || parts[0] != "STADE") continue
            val peerId = parts[1]
            val peerPort = parts[2].toIntOrNull() ?: continue
            if (peerId == nodeIdProvider()) continue
            val address = "lan://${packet.address.hostAddress}:$peerPort"
            synchronized(peers) { peers[address] = System.currentTimeMillis() }
        }
    }

    private suspend fun broadcastLoop(sock: DatagramSocket) {
        while (scope.isActive) {
            val msg = "STADE|${nodeIdProvider()}|${tcpPortProvider()}".toByteArray()
            for (addr in broadcastAddresses()) {
                val packet = DatagramPacket(msg, msg.size, addr, port)
                runCatching { sock.send(packet) }
            }
            kotlinx.coroutines.delay(15_000)
        }
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val out = mutableListOf<InetAddress>()
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    ia.broadcast?.let { out += it }
                }
            }
        }
        return out
    }
}
