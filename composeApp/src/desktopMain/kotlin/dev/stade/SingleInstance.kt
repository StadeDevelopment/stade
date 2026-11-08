package dev.stade

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import kotlin.concurrent.thread

private const val MAX_IPC_PAYLOAD_CHARS = 64 * 1024
private const val IPC_READ_TIMEOUT_MS = 5_000
private const val MAX_IPC_CLIENTS = 4

object SingleInstance {

    private val liveClients = java.util.concurrent.Semaphore(MAX_IPC_CLIENTS)

    @Volatile private var lockChannelRef: RandomAccessFile? = null
    @Volatile private var lockRef: FileLock? = null
    @Volatile private var serverRef: ServerSocket? = null

    fun acquireOrForward(appRoot: File, payload: String?, onPayload: (String) -> Unit): Boolean {
        if (!appRoot.exists()) appRoot.mkdirs()
        val lockFile = File(appRoot, "single.lock")
        val portFile = File(appRoot, "single.port")

        val raf = RandomAccessFile(lockFile, "rw")
        val lock = runCatching { raf.channel.tryLock() }.getOrNull()
        if (lock == null) {
            runCatching { raf.close() }
            forwardToPrimary(portFile, payload)
            return false
        }

        lockChannelRef = raf
        lockRef = lock
        val loopback = InetAddress.getByName("127.0.0.1")
        val server = ServerSocket(0, 8, loopback)
        serverRef = server
        portFile.writeText(server.localPort.toString())

        thread(isDaemon = true, name = "stade-single-instance") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                if (!liveClients.tryAcquire()) {
                    runCatching { socket.close() }
                    continue
                }
                thread(isDaemon = true) {
                    try {
                        handleClient(socket, onPayload)
                    } finally {
                        liveClients.release()
                    }
                }
            }
        }
        return true
    }

    private fun handleClient(socket: Socket, onPayload: (String) -> Unit) {
        socket.use {
            runCatching { it.soTimeout = IPC_READ_TIMEOUT_MS }
            val line = runCatching {
                val reader = BufferedReader(InputStreamReader(it.getInputStream(), Charsets.UTF_8))
                val builder = StringBuilder()
                while (builder.length < MAX_IPC_PAYLOAD_CHARS) {
                    val ch = reader.read()
                    if (ch < 0 || ch == '\n'.code) break
                    builder.append(ch.toChar())
                }
                builder.toString()
            }.getOrNull()
            val text = line?.trim()
            if (!text.isNullOrEmpty()) onPayload(text)
        }
    }

    private fun forwardToPrimary(portFile: File, payload: String?) {
        val port = runCatching { portFile.readText().trim().toInt() }.getOrNull() ?: return
        runCatching {
            Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
                val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
                writer.write(payload ?: "")
                writer.write("\n")
                writer.flush()
            }
        }
    }
}
