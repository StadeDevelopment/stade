package dev.stade.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals

private fun withDb(body: (StadeDb) -> Unit) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    try {
        StadeDb.Schema.create(driver)
        body(StadeDb(driver))
    } finally {
        driver.close()
    }
}

private fun StadeDb.contact(id: String) = stadeDbQueries.insertContact(
    id, "local", id, ByteArray(0), ByteArray(0), ByteArray(0), ByteArray(0), ByteArray(0),
    null, 0L, 0L, 0L, 0L, "", 0L, null
)

class ReactionsByConversationTest {

    @Test
    fun oneQueryReturnsEveryReactionInTheChatAndNothingElse() = withDb { db ->
        db.contact("alice")
        db.contact("bob")
        db.stadeDbQueries.insertMessage("a1", "alice", "IN", "x", 1L, 0L, 0L, null)
        db.stadeDbQueries.insertMessage("a2", "alice", "OUT", "y", 2L, 0L, 0L, null)
        db.stadeDbQueries.insertMessage("b1", "bob", "IN", "z", 3L, 0L, 0L, null)
        db.stadeDbQueries.upsertReaction("a1", "alice", "👍")
        db.stadeDbQueries.upsertReaction("a1", "me", "❤")
        db.stadeDbQueries.upsertReaction("a2", "alice", "😂")
        db.stadeDbQueries.upsertReaction("b1", "bob", "🔥")

        val grouped = db.stadeDbQueries.selectReactionsForChat("alice").executeAsList().groupBy { it.messageId }

        assertEquals(setOf("a1", "a2"), grouped.keys, "only this chat's messages")
        assertEquals(2, grouped["a1"]?.size)
        assertEquals(1, grouped["a2"]?.size)
        listOf("a1", "a2").forEach { id ->
            assertEquals(
                db.stadeDbQueries.selectReactionsForMessage(id).executeAsList().toSet(),
                grouped[id]?.toSet(),
                "the grouped result must match what the per-message query returned"
            )
        }
    }

    @Test
    fun groupReactionsStayInsideTheirGroup() = withDb { db ->
        db.stadeDbQueries.insertGroup("g1", "local", "One", "t", 0L, "c")
        db.stadeDbQueries.insertGroup("g2", "local", "Two", "t", 0L, "c")
        db.stadeDbQueries.insertGroupMessage("m1", "g1", "s", "x", 1L, 0L, 0L)
        db.stadeDbQueries.insertGroupMessage("m2", "g2", "s", "y", 2L, 0L, 0L)
        db.stadeDbQueries.upsertReaction("m1", "s", "👍")
        db.stadeDbQueries.upsertReaction("m2", "s", "👎")

        val g1 = db.stadeDbQueries.selectReactionsForGroup("g1").executeAsList()

        assertEquals(listOf("m1"), g1.map { it.messageId })
    }
}
