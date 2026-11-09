package dev.stade.transport

import kotlinx.datetime.Clock

const val MAX_CONCURRENT_HANDSHAKES = 8
const val MAX_HANDSHAKES_PER_WINDOW = 60
const val HANDSHAKE_WINDOW_MS = 60_000L

class InboundAcceptLimiter(
    private val maxConcurrent: Int = MAX_CONCURRENT_HANDSHAKES,
    private val maxPerWindow: Int = MAX_HANDSHAKES_PER_WINDOW,
    private val windowMs: Long = HANDSHAKE_WINDOW_MS,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val recent = ArrayDeque<Long>()
    private var inFlight = 0

    fun tryAcquire(): Boolean = synchronized(recent) {
        val now = nowMs()
        while (recent.isNotEmpty() && now - recent.first() >= windowMs) recent.removeFirst()
        if (inFlight >= maxConcurrent) return false
        if (recent.size >= maxPerWindow) return false
        recent.addLast(now)
        inFlight += 1
        true
    }

    fun release() {
        synchronized(recent) { if (inFlight > 0) inFlight -= 1 }
    }

    fun inFlight(): Int = synchronized(recent) { inFlight }

    fun acceptedInWindow(): Int = synchronized(recent) {
        val now = nowMs()
        while (recent.isNotEmpty() && now - recent.first() >= windowMs) recent.removeFirst()
        recent.size
    }
}
