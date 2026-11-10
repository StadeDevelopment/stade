package dev.stade.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinDotsTest {

    @Test
    fun anEmptyPinShowsSixPlaceholders() {
        assertEquals(6, pinDotCount(0), "the empty indicator must reflect the six-digit minimum")
    }

    @Test
    fun theIndicatorNeverShowsFewerDotsThanTheMinimum() {
        (0..PIN_MIN).forEach { filled ->
            assertEquals(PIN_MIN, pinDotCount(filled), "with $filled typed the row must still show $PIN_MIN")
        }
    }

    @Test
    fun longerPinsGrowTheRowUpToItsCap() {
        assertEquals(7, pinDotCount(7))
        assertEquals(8, pinDotCount(8))
        assertEquals(8, pinDotCount(PIN_MAX), "past the cap the row stops growing")
    }

    @Test
    fun theMinimumIsSixAndTheMaximumLeavesRoomAboveIt() {
        assertEquals(6, PIN_MIN)
        assertTrue(PIN_MAX > PIN_MIN)
    }
}
