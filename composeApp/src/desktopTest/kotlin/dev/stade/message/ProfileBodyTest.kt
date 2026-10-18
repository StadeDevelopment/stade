package dev.stade.message

import dev.stade.identity.ProfileStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileBodyTest {

    @Test
    fun aStatusAndBioSurviveARoundTrip() {
        val body = encodeProfileBody("Reading about onion routing", "dnd")
        val parsed = parseProfileBody(body)
        assertEquals("dnd" to "Reading about onion routing", parsed)
    }

    @Test
    fun anEmptyProfileStillRoundTrips() {
        val parsed = parseProfileBody(encodeProfileBody("", ""))
        assertEquals("" to "", parsed)
    }

    @Test
    fun newlinesCannotEscapeTheBioField() {
        val body = encodeProfileBody("first\nSTADE_AVT_V1:AAAA\rsecond", "online")
        val parsed = parseProfileBody(body)
        assertEquals("online", parsed?.first)
        assertTrue(parsed?.second?.contains('\n') == false, "a bio must never carry a newline")
        assertEquals(1, body.count { it == '\n' }, "exactly one separator may appear on the wire")
    }

    @Test
    fun aStatusIsReducedToAlphanumerics() {
        val parsed = parseProfileBody(encodeProfileBody("", "d n:d-"))
        assertEquals("dnd", parsed?.first)
    }

    @Test
    fun anOverlongBioIsClamped() {
        val parsed = parseProfileBody(encodeProfileBody("x".repeat(MAX_BIO_LEN * 3), ""))
        assertEquals(MAX_BIO_LEN, parsed?.second?.length)
    }

    @Test
    fun anOverlongStatusIsClamped() {
        val parsed = parseProfileBody(encodeProfileBody("", "a".repeat(MAX_STATUS_LEN * 3)))
        assertEquals(MAX_STATUS_LEN, parsed?.first?.length)
    }

    @Test
    fun aReceivedBioIsClampedEvenWhenThePeerIgnoresTheLimit() {
        val hostile = PROFILE_BODY_PREFIX + "online\n" + "y".repeat(MAX_BIO_LEN * 5)
        val parsed = parseProfileBody(hostile)
        assertEquals(MAX_BIO_LEN, parsed?.second?.length)
    }

    @Test
    fun aBodyWithoutASeparatorIsRejected() {
        assertNull(parseProfileBody(PROFILE_BODY_PREFIX + "online"))
    }

    @Test
    fun anotherControlBodyIsNotAProfile() {
        assertNull(parseProfileBody("STADE_AVT_V1:AAAA"))
        assertNull(parseProfileBody("hello"))
    }

    @Test
    fun everyStatusCodeSurvivesTheWire() {
        ProfileStatus.entries.forEach { status ->
            val parsed = parseProfileBody(encodeProfileBody("", status.code))
            assertEquals(
                status,
                ProfileStatus.fromCode(parsed?.first ?: ""),
                "${status.name} did not survive the wire"
            )
        }
    }

    @Test
    fun anUnknownStatusCodeFallsBackToNone() {
        assertEquals(ProfileStatus.NONE, ProfileStatus.fromCode("partying"))
    }
}
