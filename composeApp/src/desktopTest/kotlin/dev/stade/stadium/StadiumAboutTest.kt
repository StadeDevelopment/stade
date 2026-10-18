package dev.stade.stadium

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.stade.crypto.platformCrypto
import dev.stade.db.StadeDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val OWNER_ID = "owner"
private const val CREATOR_ID = "STADE-AAAA-BBBB-CCCC"
private const val STRANGER_ID = "STADE-DDDD-EEEE-FFFF"

class StadiumAboutTest {

    private fun manager(): StadiumManager {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        StadeDb.Schema.create(driver)
        return StadiumManager(StadeDb(driver), platformCrypto())
    }

    private fun StadiumManager.seedFollowed(id: String = "std1"): String {
        storePendingJoin(CREATOR_ID, PendingStadiumJoin(id, "Field notes", "token"))
        handleStadiumWelcome(OWNER_ID, CREATOR_ID, "$STD_WELCOME_PREFIX$id:3:Field notes")
        return id
    }

    @Test
    fun theCreatorCanSetTheAboutOfAStadiumWeFollow() {
        val stadiums = manager()
        val id = stadiums.seedFollowed()

        val applied = stadiums.handleAboutUpdate(CREATOR_ID, "$STD_ABOUT_PREFIX$id:Daily notes\nfrom the field")

        assertTrue(applied)
        assertEquals("Daily notes\nfrom the field", stadiums.getStadium(id)?.about)
    }

    @Test
    fun aStrangerCannotRewriteTheAbout() {
        val stadiums = manager()
        val id = stadiums.seedFollowed()
        stadiums.handleAboutUpdate(CREATOR_ID, "$STD_ABOUT_PREFIX$id:Real description")

        val applied = stadiums.handleAboutUpdate(STRANGER_ID, "$STD_ABOUT_PREFIX$id:Spoofed description")

        assertFalse(applied, "only the Stadium creator may change its About")
        assertEquals("Real description", stadiums.getStadium(id)?.about)
    }

    @Test
    fun ourOwnStadiumIsNeverRewrittenByAPeer() {
        val stadiums = manager()
        val mine = stadiums.createStadium(OWNER_ID, CREATOR_ID, "My Stadium")
        stadiums.setAbout(mine.id, "Mine to describe")

        val applied = stadiums.handleAboutUpdate(CREATOR_ID, "$STD_ABOUT_PREFIX${mine.id}:Not yours")

        assertFalse(applied, "a Stadium we own must not take an About from the wire")
        assertEquals("Mine to describe", stadiums.getStadium(mine.id)?.about)
    }

    @Test
    fun anOverlongAboutIsClamped() {
        val stadiums = manager()
        val id = stadiums.seedFollowed()

        stadiums.handleAboutUpdate(CREATOR_ID, "$STD_ABOUT_PREFIX$id:${"x".repeat(MAX_STADIUM_ABOUT_LEN * 4)}")

        assertEquals(MAX_STADIUM_ABOUT_LEN, stadiums.getStadium(id)?.about?.length)
    }

    @Test
    fun spoofingCharactersAreStrippedFromAReceivedAbout() {
        val stadiums = manager()
        val id = stadiums.seedFollowed()

        stadiums.handleAboutUpdate(CREATOR_ID, "$STD_ABOUT_PREFIX$id:safe‮evil​")

        val about = stadiums.getStadium(id)?.about
        assertEquals("safeevil", about)
    }

    @Test
    fun anUnknownStadiumIsIgnored() {
        val stadiums = manager()

        assertFalse(stadiums.handleAboutUpdate(CREATOR_ID, "${STD_ABOUT_PREFIX}missing:text"))
        assertFalse(stadiums.handleAboutUpdate(CREATOR_ID, STD_ABOUT_PREFIX))
    }

    @Test
    fun onlyAnOwnedStadiumProducesAnAboutBody() {
        val stadiums = manager()
        val followed = stadiums.seedFollowed()
        val mine = stadiums.createStadium(OWNER_ID, CREATOR_ID, "My Stadium")
        stadiums.setAbout(mine.id, "Mine to describe")

        assertEquals(null, stadiums.aboutBody(followed))
        assertEquals("$STD_ABOUT_PREFIX${mine.id}:Mine to describe", stadiums.aboutBody(mine.id))
    }
}
