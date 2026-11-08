package dev.stade.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun withDb(body: (StadeDb) -> Unit) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    try {
        StadeDb.Schema.create(driver)
        body(StadeDb(driver))
    } finally {
        driver.close()
    }
}

private fun StadeDb.addStadium(id: String, creator: String) {
    stadeDbQueries.insertStadium(id, "local", id, creator, 0L, "token", 0L, 0L)
}

private fun StadeDb.addContact(id: String) {
    stadeDbQueries.insertContact(
        id, "local", id, ByteArray(0), ByteArray(0), ByteArray(0), ByteArray(0), ByteArray(0),
        null, 0L, 0L, 0L, 0L, "", 0L, null
    )
}

private fun StadeDb.deliveredFlag(messageId: String): Long =
    stadeDbQueries.selectMessages("alice").executeAsList().first { it.id == messageId }.delivered

private fun StadeDb.stadiumMessageCount(stadiumId: String): Int =
    stadeDbQueries.selectStadiumMessages(stadiumId).executeAsList().size

class ChannelScopeTest {

    @Test
    fun aStadiumOwnerCannotDeleteAnotherChannelsMessage() = withDb { db ->
        db.addStadium("attacker-channel", "attacker")
        db.addStadium("victim-channel", "someone-else")
        db.stadeDbQueries.insertStadiumMessage("target", "victim-channel", "secret", 0L, 0L)

        db.stadeDbQueries.deleteStadiumMessage("target", "attacker-channel")

        assertEquals(
            1,
            db.stadiumMessageCount("victim-channel"),
            "a delete aimed at another channel must not remove the message"
        )

        db.stadeDbQueries.deleteStadiumMessage("target", "victim-channel")
        assertEquals(
            0,
            db.stadiumMessageCount("victim-channel"),
            "the owning channel must still be able to delete it"
        )
    }

    @Test
    fun deliveryReceiptsOnlyTouchTheSendersOwnChat() = withDb { db ->
        db.addContact("alice")
        db.addContact("bob")
        db.stadeDbQueries.insertMessage("m1", "alice", "OUT", "to alice", 0L, 0L, 0L, null)

        db.stadeDbQueries.markDelivered("m1", "bob")
        assertEquals(
            0L,
            db.deliveredFlag("m1"),
            "another contact must not be able to mark this message delivered"
        )

        db.stadeDbQueries.markDelivered("m1", "alice")
        assertEquals(
            1L,
            db.deliveredFlag("m1"),
            "the real recipient must still mark it delivered"
        )
    }

    @Test
    fun replayProtectionIsPerContact() = withDb { db ->
        db.stadeDbQueries.markEnvelopeProcessed("shared-id", "alice", 0L)

        assertTrue(db.stadeDbQueries.envelopeProcessed("shared-id", "alice").executeAsOne() > 0L)
        assertEquals(
            0L,
            db.stadeDbQueries.envelopeProcessed("shared-id", "bob").executeAsOne(),
            "one contact must not be able to suppress another contact's message id"
        )

        db.stadeDbQueries.markEnvelopeProcessed("shared-id", "bob", 0L)
        assertTrue(db.stadeDbQueries.envelopeProcessed("shared-id", "bob").executeAsOne() > 0L)

        db.stadeDbQueries.forgetProcessedEnvelope("shared-id", "bob")
        assertTrue(
            db.stadeDbQueries.envelopeProcessed("shared-id", "alice").executeAsOne() > 0L,
            "forgetting one contact's envelope must leave the other intact"
        )
    }

    @Test
    fun reactionTargetsAreConfinedToTheirOwnChat() = withDb { db ->
        db.addContact("alice")
        db.stadeDbQueries.insertMessage("m1", "alice", "IN", "hello", 0L, 0L, 0L, null)
        db.stadeDbQueries.insertGroup("g1", "local", "Group", "token", 0L, "creator")
        db.stadeDbQueries.insertGroupMessage("gm1", "g1", "someone", "hi", 0L, 0L, 0L)

        assertTrue(db.stadeDbQueries.messageInChat("m1", "alice").executeAsOne() > 0L)
        assertEquals(
            0L,
            db.stadeDbQueries.messageInChat("m1", "bob").executeAsOne(),
            "a message must not look like it belongs to another chat"
        )
        assertTrue(db.stadeDbQueries.groupMessageInGroup("gm1", "g1").executeAsOne() > 0L)
        assertEquals(
            0L,
            db.stadeDbQueries.groupMessageInGroup("gm1", "g2").executeAsOne(),
            "a group message must not look like it belongs to another group"
        )
    }
}
