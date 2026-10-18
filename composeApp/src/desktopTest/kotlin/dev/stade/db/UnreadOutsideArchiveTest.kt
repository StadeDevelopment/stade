package dev.stade.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals

class UnreadOutsideArchiveTest {

    private fun withDb(block: (StadeDb) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            StadeDb.Schema.create(driver)
            val db = StadeDb(driver)
            db.stadeDbQueries.insertContact(
                "c1", "me", "A", ByteArray(0), ByteArray(0), ByteArray(0), ByteArray(0),
                ByteArray(0), null, 0L, 0L, 0L, 0L, "", 0L, null
            )
            db.stadeDbQueries.insertContact(
                "c2", "me", "B", ByteArray(0), ByteArray(0), ByteArray(0), ByteArray(0),
                ByteArray(0), null, 0L, 0L, 0L, 0L, "", 0L, null
            )
            block(db)
        } finally {
            driver.close()
        }
    }

    private fun StadeDb.unread(id: String, contactId: String) {
        stadeDbQueries.insertMessage(id, contactId, "IN", "hi", 0L, 0L, 0L, null)
    }

    private fun StadeDb.archive(ownerId: String, chatKey: String) {
        stadeDbQueries.putKv("arch.$ownerId.$chatKey", "1".encodeToByteArray())
    }

    @Test
    fun unreadInAnArchivedChatIsNotCounted() = withDb { db ->
        db.unread("m1", "c1")
        db.unread("m2", "c2")
        assertEquals(2L, db.stadeDbQueries.countUnreadOutsideArchive().executeAsOne())

        db.archive("me", "c1")
        assertEquals(
            1L,
            db.stadeDbQueries.countUnreadOutsideArchive().executeAsOne(),
            "an archived chat must not add to the count a notification shows"
        )
    }

    @Test
    fun unarchivingBringsTheCountBack() = withDb { db ->
        db.unread("m1", "c1")
        db.archive("me", "c1")
        assertEquals(0L, db.stadeDbQueries.countUnreadOutsideArchive().executeAsOne())

        db.stadeDbQueries.deleteKv("arch.me.c1")
        assertEquals(1L, db.stadeDbQueries.countUnreadOutsideArchive().executeAsOne())
    }

    @Test
    fun anArchiveEntryOfAnotherIdentityDoesNotSilenceThisOne() = withDb { db ->
        db.unread("m1", "c1")
        db.archive("someone-else", "c1")
        assertEquals(
            1L,
            db.stadeDbQueries.countUnreadOutsideArchive().executeAsOne(),
            "the archive is per identity, so another identity's entry must not count"
        )
    }

    @Test
    fun readMessagesAndOutgoingOnesAreNeverCounted() = withDb { db ->
        db.stadeDbQueries.insertMessage("m1", "c1", "IN", "hi", 0L, 0L, 1L, null)
        db.stadeDbQueries.insertMessage("m2", "c1", "OUT", "hi", 0L, 0L, 0L, null)
        assertEquals(0L, db.stadeDbQueries.countUnreadOutsideArchive().executeAsOne())
    }
}
