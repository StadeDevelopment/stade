package dev.stade.link

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.stade.db.StadeDb
import dev.stade.message.getTypingSignalEnabled
import dev.stade.message.setTypingSignalEnabled
import kotlin.test.Test
import kotlin.test.assertFalse
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

class PrivacyDefaultsTest {

    @Test
    fun aFreshInstallDoesNotFetchPreviewsForLinksItReceives() = withDb { db ->
        assertFalse(
            getLinkPreviewsEnabled(db),
            "fetching a received link tells the sender's server the message was opened"
        )
    }

    @Test
    fun previewsStayOffUntilTheyAreTurnedOnExplicitly() = withDb { db ->
        setLinkPreviewsEnabled(db, true)
        assertTrue(getLinkPreviewsEnabled(db))
        setLinkPreviewsEnabled(db, false)
        assertFalse(getLinkPreviewsEnabled(db))
    }

    @Test
    fun theTypingSignalCanBeTurnedOff() = withDb { db ->
        assertTrue(getTypingSignalEnabled(db), "sharing typing stays the default behaviour")

        setTypingSignalEnabled(db, false)

        assertFalse(
            getTypingSignalEnabled(db),
            "there must be a way to stop telling a contact when you are writing"
        )
    }
}
