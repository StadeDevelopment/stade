package dev.stade.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private class FakeClock(var now: Long = 0L)

class RotatingNodeIdTest {

    private fun rotating(seed: String, clock: FakeClock) =
        RotatingNodeId(seed, rotationMs = 1_000L, nowMs = { clock.now })

    @Test
    fun theSameSlotKeepsTheSameIdSoPeersStayReachable() {
        val clock = FakeClock()
        val id = rotating("seed", clock)
        val first = id.current()
        clock.now += 999L

        assertEquals(first, id.current(), "the id must be stable inside a rotation window")
    }

    @Test
    fun theIdChangesOnceTheWindowPasses() {
        val clock = FakeClock()
        val id = rotating("seed", clock)
        val first = id.current()
        clock.now += 1_000L

        assertNotEquals(
            first,
            id.current(),
            "a permanent broadcast id lets anyone link the same device across networks"
        )
    }

    @Test
    fun twoDevicesNeverShareAnId() {
        val clock = FakeClock()
        assertNotEquals(rotating("device-a", clock).current(), rotating("device-b", clock).current())
    }

    @Test
    fun theIdIsOpaqueAndDoesNotLeakTheSeed() {
        val clock = FakeClock()
        val value = rotating("STADE-AAAA-BBBB-CCCC", clock).current()

        assertTrue(value.isNotEmpty())
        assertTrue(value.length <= 32)
        assertTrue(
            !value.contains("STADE", ignoreCase = true),
            "the broadcast id must not carry the identity it was derived from"
        )
    }

    @Test
    fun theDefaultRotationIsNotEffectivelyPermanent() {
        assertTrue(
            NODE_ID_ROTATION_MS <= 24L * 60 * 60 * 1000,
            "rotating once a day at most keeps cross-network linkage short-lived"
        )
    }
}
