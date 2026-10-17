package dev.stade.message

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val RETIRED_MEME_BODY = "STADE_MEM_V1:alikoc|23000|QUJDRA=="

private fun message(body: String) = Message(
    id = "m1",
    contactId = "c1",
    direction = MessageDirection.IN,
    body = body,
    timestamp = 0L,
    delivered = true,
    read = true
)

class RetiredBodyTest {

    @Test
    fun aRetiredMemeBodyIsReportedAsUnsupported() {
        assertEquals(MessageType.UNSUPPORTED, message(RETIRED_MEME_BODY).type)
    }

    @Test
    fun aRetiredBodyNeverRendersAsPlainText() {
        val type = message(RETIRED_MEME_BODY).type
        assertFalse(type == MessageType.TEXT, "base64 payload must not leak into a text bubble")
    }

    @Test
    fun retiredBodiesAreRecognisedInsideAReplyWrapper() {
        val wrapped = encodeReplyBody("parent-id", RETIRED_MEME_BODY)
        assertEquals(MessageType.UNSUPPORTED, message(wrapped).type)
    }

    @Test
    fun ordinaryBodiesAreNotRetired() {
        assertFalse(isRetiredBody("hello there"))
        assertTrue(isRetiredBody(RETIRED_MEME_BODY))
    }

    @Test
    fun previewFallsBackToTheUnsupportedLabel() {
        assertEquals(
            "not supported",
            previewBody(RETIRED_MEME_BODY, "photo", unsupportedLabel = "not supported")
        )
    }

    @Test
    fun retiredPadSoundsAreTreatedAsUnsupported() {
        val padBody = PAD_SOUND_BODY_PREFIX + "whistle|900|QQ=="
        assertTrue(isRetiredBody(padBody))
        assertEquals(MessageType.UNSUPPORTED, message(padBody).type)
        assertEquals(
            "not supported",
            previewBody(padBody, "photo", unsupportedLabel = "not supported")
        )
    }
}
