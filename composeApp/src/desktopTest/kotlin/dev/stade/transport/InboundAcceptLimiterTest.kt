package dev.stade.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class Clock(var now: Long = 0L)

class InboundAcceptLimiterTest {

    @Test
    fun aFloodIsCappedAtTheConcurrencyLimit() {
        val limiter = InboundAcceptLimiter(maxConcurrent = 3, maxPerWindow = 1000)

        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire(), "a fourth simultaneous handshake must be refused")
        assertEquals(3, limiter.inFlight())
    }

    @Test
    fun finishedHandshakesFreeTheirSlot() {
        val limiter = InboundAcceptLimiter(maxConcurrent = 2, maxPerWindow = 1000)
        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())

        limiter.release()

        assertTrue(limiter.tryAcquire(), "a completed handshake must let the next one in")
        assertEquals(2, limiter.inFlight())
    }

    @Test
    fun aSlowDripIsStillCappedOverTime() {
        val clock = Clock()
        val limiter = InboundAcceptLimiter(
            maxConcurrent = 100,
            maxPerWindow = 5,
            windowMs = 1_000L,
            nowMs = { clock.now }
        )

        repeat(5) {
            assertTrue(limiter.tryAcquire(), "the first five must be accepted")
            limiter.release()
        }
        assertFalse(
            limiter.tryAcquire(),
            "connecting repeatedly must not get unlimited key generation out of us"
        )
    }

    @Test
    fun theRateBudgetRefillsAsTheWindowPasses() {
        val clock = Clock()
        val limiter = InboundAcceptLimiter(
            maxConcurrent = 100,
            maxPerWindow = 2,
            windowMs = 1_000L,
            nowMs = { clock.now }
        )
        assertTrue(limiter.tryAcquire()); limiter.release()
        assertTrue(limiter.tryAcquire()); limiter.release()
        assertFalse(limiter.tryAcquire())

        clock.now += 1_000L

        assertTrue(limiter.tryAcquire(), "a legitimate peer must get in once the window has passed")
        assertEquals(1, limiter.acceptedInWindow())
    }

    @Test
    fun releaseNeverDropsBelowZero() {
        val limiter = InboundAcceptLimiter(maxConcurrent = 1, maxPerWindow = 10)
        limiter.release()
        limiter.release()
        assertEquals(0, limiter.inFlight())
        assertTrue(limiter.tryAcquire())
    }
}
