package dev.stade.chat

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.stade.db.StadeDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinnedMessagesTest {

    private fun store(): Pair<StadeDb, PinnedMessages> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        StadeDb.Schema.create(driver)
        val db = StadeDb(driver)
        return db to PinnedMessages(db)
    }

    private fun StadeDb.pinAt(chatId: String, messageId: String, at: Long) {
        stadeDbQueries.putKv("msgpin.$chatId.$messageId", at.toString().encodeToByteArray())
    }

    @Test
    fun aPinnedMessageIsRememberedUntilItIsUnpinned() {
        val (_, pins) = store()

        pins.setPinned("chat-1", "m1", true)
        assertEquals(listOf("m1"), pins.pinned("chat-1").map { it.messageId })

        pins.setPinned("chat-1", "m1", false)
        assertTrue(pins.pinned("chat-1").isEmpty())
    }

    @Test
    fun theNewestPinComesFirst() {
        val (db, pins) = store()
        db.pinAt("chat-1", "older", 1_000)
        db.pinAt("chat-1", "newer", 2_000)

        assertEquals(listOf("newer", "older"), pins.pinned("chat-1").map { it.messageId })
    }

    @Test
    fun pinsBelongToOneChatOnly() {
        val (_, pins) = store()
        pins.setPinned("chat-1", "m1", true)
        pins.setPinned("chat-2", "m2", true)

        assertEquals(listOf("m1"), pins.pinned("chat-1").map { it.messageId })
        assertEquals(listOf("m2"), pins.pinned("chat-2").map { it.messageId })
    }

    @Test
    fun aChatIdThatPrefixesAnotherDoesNotStealItsPins() {
        val (_, pins) = store()
        pins.setPinned("abc", "m1", true)
        pins.setPinned("abcd", "m2", true)

        assertEquals(listOf("m1"), pins.pinned("abc").map { it.messageId })
        assertEquals(listOf("m2"), pins.pinned("abcd").map { it.messageId })
    }

    @Test
    fun unpinAllClearsOnlyTheGivenChat() {
        val (_, pins) = store()
        pins.setPinned("chat-1", "m1", true)
        pins.setPinned("chat-1", "m2", true)
        pins.setPinned("chat-2", "m3", true)

        pins.unpinAll("chat-1")

        assertTrue(pins.pinned("chat-1").isEmpty())
        assertEquals(listOf("m3"), pins.pinned("chat-2").map { it.messageId })
    }

    @Test
    fun aBlankTargetIsIgnored() {
        val (_, pins) = store()

        pins.setPinned("", "m1", true)
        pins.setPinned("chat-1", "", true)

        assertTrue(pins.pinned("chat-1").isEmpty())
    }

    @Test
    fun aDamagedPinRowIsSkipped() {
        val (db, pins) = store()
        db.stadeDbQueries.putKv("msgpin.chat-1.m1", "not-a-timestamp".encodeToByteArray())
        db.pinAt("chat-1", "m2", 5_000)

        assertEquals(listOf("m2"), pins.pinned("chat-1").map { it.messageId })
    }
}
