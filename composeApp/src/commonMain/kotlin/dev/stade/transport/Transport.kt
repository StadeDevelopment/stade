package dev.stade.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TransportType { TOR, LAN }

data class TransportInfo(
    val type: TransportType,
    val displayName: String,
    val available: Boolean,
    val running: Boolean,
    val message: String = "",
    val bootstrapPercent: Int? = null,
    val bootstrapPhase: String = "",
    val failed: Boolean = false
)

interface Connection {
    val remoteAddress: String
    suspend fun send(frame: ByteArray)
    suspend fun receive(): ByteArray?
    suspend fun close()
    val lastProgressAtMs: Long get() = 0L
}

interface IncomingConnections {
    suspend fun accept(): Connection?
}

interface TransportPlugin {
    val type: TransportType
    val info: StateFlow<TransportInfo>
    suspend fun start(handler: suspend (Connection) -> Unit)
    suspend fun stop()
    suspend fun connect(address: String): Connection?
    fun selfAddress(): String?
    fun selfAddresses(): List<String> = listOfNotNull(selfAddress())

    suspend fun reload() {}

    suspend fun refreshReachability(): Boolean = false
}

interface DiscoverableTransport {
    fun discoveredPeers(): List<String>
}

abstract class BaseTransport(override val type: TransportType, displayName: String) : TransportPlugin {
    protected val state = MutableStateFlow(TransportInfo(type, displayName, available = false, running = false))
    override val info: StateFlow<TransportInfo> = state.asStateFlow()
}

class ConnectionRegistry {
    private val plugins = mutableMapOf<TransportType, TransportPlugin>()
    fun register(plugin: TransportPlugin) { plugins[plugin.type] = plugin }
    fun get(type: TransportType): TransportPlugin? = plugins[type]
    fun all(): List<TransportPlugin> = plugins.values.toList()
}
