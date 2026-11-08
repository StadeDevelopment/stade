package dev.stade.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GifFrameBudgetTest {

    @Test
    fun aSmallAnimationKeepsAllOfItsFrames() {
        val framePixels = 128L * 128L
        assertEquals(40, gifFrameBudget(framePixels, 40))
    }

    @Test
    fun theFrameCountIsStillCappedForTinyFrames() {
        assertEquals(MAX_GIF_FRAMES, gifFrameBudget(16L * 16L, 10_000))
    }

    @Test
    fun aLargeAnimationIsTrimmedToStayInsideTheMemoryBudget() {
        val framePixels = MAX_GIF_FRAME_PIXELS
        val frames = gifFrameBudget(framePixels, MAX_GIF_FRAMES)

        assertTrue(frames < MAX_GIF_FRAMES, "a full-size frame must not be allowed 160 times over")
        assertTrue(
            frames.toLong() * framePixels <= MAX_GIF_TOTAL_PIXELS,
            "the kept frames must fit the total pixel budget"
        )
    }

    @Test
    fun theWorstCaseAnimationCannotReachAGigabyte() {
        val framePixels = MAX_GIF_FRAME_PIXELS
        val bytes = gifFrameBudget(framePixels, MAX_GIF_FRAMES).toLong() * framePixels * 4L

        assertTrue(bytes <= 128L * 1024 * 1024, "worst case was ${bytes / (1024 * 1024)} MiB")
    }

    @Test
    fun anEmptyOrBrokenFrameSizeDecodesNothing() {
        assertEquals(0, gifFrameBudget(0L, 20))
        assertEquals(0, gifFrameBudget(-1L, 20))
    }
}
