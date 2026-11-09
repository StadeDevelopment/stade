package dev.stade.contact

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.stade.db.StadeDb
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

class InviteWindowTest {

    @Test
    fun noInviteIsOpenOnAFreshInstall() = withDb { db ->
        assertFalse(
            isInviteWindowOpen(db, nowMs = 1_000L),
            "an install that has never shown an invite must not accept strangers"
        )
    }

    @Test
    fun showingAnInviteOpensTheWindow() = withDb { db ->
        openInviteWindow(db, nowMs = 1_000L)

        assertTrue(isInviteWindowOpen(db, nowMs = 1_000L))
        assertTrue(
            isInviteWindowOpen(db, nowMs = 1_000L + INVITE_WINDOW_MS - 1),
            "the window must stay open long enough to share an invite and be accepted later"
        )
    }

    @Test
    fun aLeakedInviteStopsWorkingOnceTheWindowPasses() = withDb { db ->
        openInviteWindow(db, nowMs = 1_000L)

        assertFalse(
            isInviteWindowOpen(db, nowMs = 1_000L + INVITE_WINDOW_MS),
            "an invite that leaks must not still admit someone weeks later"
        )
    }

    @Test
    fun showingAnotherInviteExtendsTheWindow() = withDb { db ->
        openInviteWindow(db, nowMs = 0L)
        val halfway = INVITE_WINDOW_MS / 2
        openInviteWindow(db, nowMs = halfway)

        assertTrue(isInviteWindowOpen(db, nowMs = halfway + INVITE_WINDOW_MS - 1))
    }

    @Test
    fun theWindowCanBeClosedDeliberately() = withDb { db ->
        openInviteWindow(db, nowMs = 0L)
        assertTrue(isInviteWindowOpen(db, nowMs = 1L))

        closeInviteWindow(db)

        assertFalse(isInviteWindowOpen(db, nowMs = 1L))
    }

    @Test
    fun theWindowIsNotEffectivelyPermanent() {
        assertTrue(
            INVITE_WINDOW_MS <= 30L * 24 * 60 * 60 * 1000,
            "a window measured in months would be the same as no window at all"
        )
    }
}
