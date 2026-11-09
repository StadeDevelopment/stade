package dev.stade.transport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LargeFrameOverSocketTest {

    private fun frameOf(size: Int): ByteArray =
        ByteArray(size) { ((it * 37) and 0xff).toByte() }

    private suspend fun roundTrip(scope: kotlinx.coroutines.CoroutineScope, size: Int): ByteArray? {
        val port = 45_900 + (size % 40)
        val transport = LanTransport(nodeId = "frame-test", tcpPort = port, discoveryPort = port + 500)
        val accepted = CompletableDeferred<Connection>()
        try {
            transport.start { conn -> accepted.complete(conn) }
            val info = withTimeoutOrNull(10_000) {
                while (!transport.info.value.running) delay(20)
                transport.info.value
            }
            assertNotNull(info, "the listener must come up")

            val bound = Regex(":(\\d+)").find(info.message)?.groupValues?.get(1)?.toInt() ?: port
            val client = withTimeoutOrNull(10_000) { transport.connect("lan://127.0.0.1:$bound") }
            assertNotNull(client, "the client must connect to the listener")

            val payload = frameOf(size)
            scope.launch { client.send(payload) }

            val server = withTimeoutOrNull(15_000) { accepted.await() }
            assertNotNull(server, "the listener must hand back a connection")
            return withTimeoutOrNull(30_000) { server.receive() }
        } finally {
            runCatching { transport.stop() }
        }
    }

    @Test
    fun asmallFrameSurvivesARealSocket() = runBlocking {
        val got = roundTrip(this, 4_096)
        assertNotNull(got, "a small frame must arrive")
        assertEquals(4_096, got.size)
    }

    @Test
    fun aStickerSizedFrameSurvivesARealSocket() = runBlocking {
        val size = 2_460_011
        val got = roundTrip(this, size)
        assertNotNull(
            got,
            "a sticker-sized frame must arrive over a real socket, not be read as a closed connection"
        )
        assertEquals(size, got.size, "the whole frame must arrive intact")
        assertContentEquals(frameOf(size), got)
    }

    @Test
    fun aFrameAtTheSendersLimitStillArrives() = runBlocking {
        val size = dev.stade.sync.FrameCodec.MAX_LEN
        val got = roundTrip(this, size)
        assertNotNull(
            got,
            "the largest frame the sender will emit must be accepted by the receiver"
        )
        assertEquals(size, got.size)
    }

    @Test
    fun everyChunkBoundaryIsCrossedCorrectly() = runBlocking {
        val sizes = listOf(65_535, 65_536, 65_537, 131_072, 200_000)
        sizes.forEach { size ->
            val got = roundTrip(this, size)
            assertNotNull(got, "a $size byte frame must arrive whole")
            assertEquals(size, got.size, "a $size byte frame must not be truncated")
            assertContentEquals(
                frameOf(size),
                got,
                "a $size byte frame must not come back padded with zeroes past the first chunk"
            )
        }
    }
}
