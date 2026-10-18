package dev.stade.ui

import dev.stade.ui.screens.stadeyHiddenFraction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StadeyPeekTest {

    @Test
    fun stadeyWaitsEntirelyOffScreen() {
        assertEquals(
            1f,
            stadeyHiddenFraction(0f),
            "at rest the mascot must be past the edge, not parked against it"
        )
    }

    @Test
    fun stadeyShowsMostOfItselfWithoutLeavingTheCorner() {
        val hidden = stadeyHiddenFraction(1f)

        assertTrue(hidden < 0.4f, "at the top of the peek the mascot must be clearly visible")
        assertTrue(hidden > 0.1f, "it peeks from the corner rather than walking onto the screen")
    }

    @Test
    fun theMascotOnlyEverMovesTowardsTheScreen() {
        var previous = stadeyHiddenFraction(0f)
        for (step in 1..20) {
            val next = stadeyHiddenFraction(step / 20f)
            assertTrue(next < previous, "the slide must not stall or reverse part way")
            previous = next
        }
    }

    @Test
    fun anOvershootingSpringNeverPushesItPastItsResting() {
        assertEquals(stadeyHiddenFraction(1f), stadeyHiddenFraction(1.3f))
        assertEquals(stadeyHiddenFraction(0f), stadeyHiddenFraction(-0.2f))
    }
}
