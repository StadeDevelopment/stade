package dev.stade.message

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.stade.crypto.platformCrypto
import dev.stade.db.StadeDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val CHAT = "contact-1"

class SearchFilterTest {

    private class Fixture {
        val db: StadeDb
        val messages: MessageManager

        init {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            StadeDb.Schema.create(driver)
            db = StadeDb(driver)
            messages = MessageManager(db, platformCrypto())
        }

        fun send(body: String, at: Long): String = messages.saveOutgoing(CHAT, body, at).id
    }

    private fun fixture() = Fixture()

    @Test
    fun theMediaFilterFindsPhotosAndVideosOnly() {
        val f = fixture()
        val photo = f.send(IMAGE_BODY_PREFIX + "AAAA\nholiday", 1_000)
        val video = f.send(VIDEO_BODY_PREFIX + "BBBB", 2_000)
        val voice = f.send(VOICE_BODY_PREFIX + "CCCC", 3_000)
        val text = f.send("just talking", 4_000)

        val hits = f.messages.filterInChat(CHAT, SearchFilter.Media)

        assertTrue(hits.contains(photo))
        assertTrue(hits.contains(video))
        assertFalse(hits.contains(voice))
        assertFalse(hits.contains(text))
    }

    @Test
    fun theVoiceAndStickerFiltersStayApart() {
        val f = fixture()
        val voice = f.send(VOICE_BODY_PREFIX + "AAAA", 1_000)
        val sticker = f.send(STICKER_BODY_PREFIX + "BBBB", 2_000)

        assertEquals(listOf(voice), f.messages.filterInChat(CHAT, SearchFilter.Voice))
        assertEquals(listOf(sticker), f.messages.filterInChat(CHAT, SearchFilter.Stickers))
    }

    @Test
    fun aMediaReplyIsStillFoundByTheFilter() {
        val f = fixture()
        val replyPhoto = f.send(encodeReplyBody("parent-id", IMAGE_BODY_PREFIX + "AAAA"), 1_000)

        assertTrue(
            f.messages.filterInChat(CHAT, SearchFilter.Media).contains(replyPhoto),
            "a photo sent as a reply must still count as media"
        )
    }

    @Test
    fun filteredResultsArriveNewestFirst() {
        val f = fixture()
        val older = f.send(IMAGE_BODY_PREFIX + "AAAA", 1_000)
        val newer = f.send(IMAGE_BODY_PREFIX + "BBBB", 2_000)

        assertEquals(listOf(newer, older), f.messages.filterInChat(CHAT, SearchFilter.Media))
    }

    @Test
    fun theAllFilterLeavesSearchingToTheTextQuery() {
        val f = fixture()
        f.send(IMAGE_BODY_PREFIX + "AAAA", 1_000)

        assertTrue(f.messages.filterInChat(CHAT, SearchFilter.All).isEmpty())
    }

    @Test
    fun textSearchNeverReturnsAnAttachmentPayload() {
        val f = fixture()
        f.send(IMAGE_BODY_PREFIX + "QUJDRA", 1_000)
        f.send(VIDEO_BODY_PREFIX + "QUJDRA", 2_000)
        f.send(VOICE_BODY_PREFIX + "QUJDRA", 3_000)
        f.send(STICKER_BODY_PREFIX + "QUJDRA", 4_000)
        val text = f.send("QUJDRA is just text here", 5_000)

        assertEquals(
            listOf(text),
            f.messages.searchInChat(CHAT, "QUJDRA"),
            "base64 attachment payloads must not surface as text hits"
        )
    }
}
