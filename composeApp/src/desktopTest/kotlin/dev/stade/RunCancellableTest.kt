package dev.stade

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunCancellableTest {

    @Test
    fun ordinaryFailuresAreStillCaptured() {
        val result = runCancellable { throw IllegalStateException("boom") }

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun successIsPassedThrough() {
        assertEquals(7, runCancellable { 7 }.getOrNull())
    }

    @Test
    fun cancellationIsNotSwallowed() {
        var seen = false
        try {
            runCancellable { throw CancellationException("stop") }
        } catch (_: CancellationException) {
            seen = true
        }
        assertTrue(seen, "a cancelled coroutine must not be told its work merely failed")
    }

    @Test
    fun aLoopUsingItStopsWhenTheScopeIsCancelled() = runTest {
        var iterations = 0
        val job = launch {
            while (isActive) {
                runCancellable { yield() }
                iterations += 1
            }
        }
        yield()
        job.cancel()
        job.join()

        assertTrue(job.isCancelled, "the job must end up cancelled, not completed normally")
        assertFalse(job.isActive)
    }

    @Test
    fun plainRunCatchingWouldHaveHiddenIt() = runTest {
        val swallowed = async {
            runCatching { throw CancellationException("stop") }.isFailure
        }
        assertTrue(
            swallowed.await(),
            "this is the behaviour being avoided: cancellation reported as a plain failure"
        )
    }
}
