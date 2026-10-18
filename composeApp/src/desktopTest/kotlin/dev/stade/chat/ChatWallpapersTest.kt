package dev.stade.chat

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.stade.crypto.platformCrypto
import dev.stade.db.StadeDb
import dev.stade.stadium.StadiumManager
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val PICTURE = ByteArray(2048) { (it % 251).toByte() }

class ChatWallpapersTest {

    private fun store(): Pair<StadeDb, ChatWallpapers> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        StadeDb.Schema.create(driver)
        val db = StadeDb(driver)
        return db to ChatWallpapers(db)
    }

    @Test
    fun aWallpaperSurvivesARoundTrip() {
        val (_, wallpapers) = store()

        assertTrue(wallpapers.set("chat-1", PICTURE))

        assertContentEquals(PICTURE, wallpapers.get("chat-1"))
    }

    @Test
    fun eachChatKeepsItsOwnWallpaper() {
        val (_, wallpapers) = store()
        val other = ByteArray(64) { 7 }
        wallpapers.set("chat-1", PICTURE)
        wallpapers.set("chat-2", other)

        assertContentEquals(PICTURE, wallpapers.get("chat-1"))
        assertContentEquals(other, wallpapers.get("chat-2"))
        assertNull(wallpapers.get("chat-3"))
    }

    @Test
    fun clearingPutsTheChatBackToTheDefaultBackground() {
        val (_, wallpapers) = store()
        wallpapers.set("chat-1", PICTURE)

        wallpapers.set("chat-1", null)

        assertNull(wallpapers.get("chat-1"))
    }

    @Test
    fun anEmptyImageCountsAsNoWallpaper() {
        val (_, wallpapers) = store()
        wallpapers.set("chat-1", PICTURE)

        wallpapers.set("chat-1", ByteArray(0))

        assertNull(wallpapers.get("chat-1"))
    }

    @Test
    fun anOversizedImageIsRefusedRatherThanStored() {
        val (_, wallpapers) = store()
        val huge = ByteArray(MAX_WALLPAPER_BYTES + 1)

        assertFalse(wallpapers.set("chat-1", huge))
        assertNull(wallpapers.get("chat-1"))
    }

    @Test
    fun aBlankChatIsIgnored() {
        val (_, wallpapers) = store()

        assertFalse(wallpapers.set("", PICTURE))
        assertNull(wallpapers.get(""))
    }

    @Test
    fun leavingAStadiumTakesItsWallpaperWithIt() {
        val (db, wallpapers) = store()
        val stadiums = StadiumManager(db, platformCrypto())
        val mine = stadiums.createStadium("owner", "creator", "Mine")
        wallpapers.set(mine.id, PICTURE)

        stadiums.deleteStadium(mine.id)

        assertNull(wallpapers.get(mine.id), "a removed Stadium must not leave its wallpaper behind")
    }

    @Test
    fun clearAllRemovesEveryWallpaper() {
        val (_, wallpapers) = store()
        wallpapers.set("chat-1", PICTURE)
        wallpapers.set("chat-2", PICTURE)

        wallpapers.clearAll()

        assertEquals(listOf(null, null), listOf(wallpapers.get("chat-1"), wallpapers.get("chat-2")))
    }
}
