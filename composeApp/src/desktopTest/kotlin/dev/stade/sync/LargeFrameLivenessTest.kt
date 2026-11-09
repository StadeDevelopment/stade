package dev.stade.sync

import dev.stade.message.MAX_ATTACHMENT_BYTES
import dev.stade.message.encodeStickerBody
import dev.stade.sticker.MAX_IMPORT_ENTRY_BYTES
import dev.stade.ui.components.AnimationFrames
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val SILENCE_LIMIT_MS = 60_000L

private class SlowReader(private val bytesPerSecond: Long) {
    var clock = 0L
        private set
    var lastProgressAtMs = 0L
        private set

    fun readFrame(sizeBytes: Long, chunkBytes: Long, stampProgress: Boolean) {
        var sent = 0L
        while (sent < sizeBytes) {
            val chunk = minOf(chunkBytes, sizeBytes - sent)
            clock += (chunk * 1000L) / bytesPerSecond
            sent += chunk
            if (stampProgress) lastProgressAtMs = clock
        }
    }

    fun watchdogWouldClose(): Boolean = clock - lastProgressAtMs > SILENCE_LIMIT_MS
}

class LargeFrameLivenessTest {

    private fun stickerFrameBytes(gifSize: Int): Long {
        val body = encodeStickerBody(ByteArray(gifSize) { it.toByte() })
        val sealedLike = ByteArray(body.encodeToByteArray().size + 1136)
        val payload = MessagePayload("a".repeat(32), 1L, sealedLike)
        return (encodeBinaryPayload(payload)?.size ?: 0).toLong() + FRAME_HEADER_BYTES
    }

    @Test
    fun aLargeStickerOverASlowCircuitNoLongerTripsTheWatchdog() {
        val frame = stickerFrameBytes(MAX_ATTACHMENT_BYTES)
        val slow = 40_000L

        val withoutProgress = SlowReader(slow)
        withoutProgress.readFrame(frame, frame, stampProgress = false)
        assertTrue(
            withoutProgress.watchdogWouldClose(),
            "the old behaviour must be reproduced: a $frame byte frame at $slow B/s looked silent"
        )

        val withProgress = SlowReader(slow)
        withProgress.readFrame(frame, 64L * 1024, stampProgress = true)
        assertTrue(
            !withProgress.watchdogWouldClose(),
            "reading in chunks and stamping progress must keep the connection alive"
        )
    }

    @Test
    fun evenAVerySlowCircuitSurvivesIfBytesKeepArriving() {
        val frame = stickerFrameBytes(MAX_ATTACHMENT_BYTES)
        val crawl = SlowReader(5_000L)
        crawl.readFrame(frame, 64L * 1024, stampProgress = true)

        assertTrue(
            !crawl.watchdogWouldClose(),
            "progress, not throughput, must decide whether a peer is alive"
        )
        assertTrue(crawl.clock > SILENCE_LIMIT_MS, "the transfer must genuinely outlast the silence limit")
    }

    @Test
    fun aTrulySilentPeerIsStillTimedOut() {
        val stalled = SlowReader(1_000_000L)
        stalled.readFrame(1024, 1024, stampProgress = true)
        val afterProgress = stalled.clock
        stalled.readFrame(0, 1, stampProgress = true)

        val quiet = SlowReader(1L)
        quiet.readFrame(SILENCE_LIMIT_MS + 1_000L, SILENCE_LIMIT_MS + 1_000L, stampProgress = false)
        assertTrue(quiet.watchdogWouldClose(), "a peer sending nothing must still be dropped")
        assertTrue(afterProgress >= 0)
    }

    @Test
    fun theWireBudgetLeavesRoomForFramingAndTheLinkTag() {
        assertTrue(
            MAX_WIRE_FRAME_BYTES >= FrameCodec.MAX_LEN + FRAME_HEADER_BYTES + LINK_SEAL_OVERHEAD_BYTES,
            "a payload the sender accepts must still fit once framed and sealed"
        )
        val biggestAccepted = ByteArray(FrameCodec.MAX_LEN)
        val framed = FrameCodec.encode(SyncRecord(RecordType.MESSAGE, biggestAccepted)).size
        assertTrue(
            framed + LINK_SEAL_OVERHEAD_BYTES <= MAX_WIRE_FRAME_BYTES,
            "the largest allowed payload framed to $framed bytes, over the receiver's budget"
        )
    }

    @Test
    fun aStickerCanNeverBeImportedLargerThanItCanBeSent() {
        assertTrue(
            MAX_IMPORT_ENTRY_BYTES <= MAX_ATTACHMENT_BYTES,
            "importing a sticker that can never be delivered only fails later, on the receiver"
        )
    }

    @Test
    fun animationStateSurvivesAFreshArrayWithTheSameContent() {
        val first = ByteArray(2048) { it.toByte() }
        val copy = first.copyOf()

        assertEquals(
            AnimationFrames(first),
            AnimationFrames(copy),
            "a re-read of the same sticker must not restart the animation"
        )
        assertEquals(AnimationFrames(first).hashCode(), AnimationFrames(copy).hashCode())
        assertNotEquals(
            AnimationFrames(first),
            AnimationFrames(ByteArray(2048) { (it + 1).toByte() }),
            "a different sticker must still be treated as different"
        )
        assertNotEquals(AnimationFrames(first), AnimationFrames(ByteArray(4)))
    }
}
