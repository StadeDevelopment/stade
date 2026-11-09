package dev.stade.sticker

import dev.stade.db.DriverFactory
import dev.stade.db.StadeDb
import dev.stade.crypto.platformCrypto
import dev.stade.ui.PickedStickerFile
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun png(size: Int, shade: Int): ByteArray {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.color = Color(shade, shade % 200, 255 - shade)
    g.fillRect(0, 0, size, size)
    g.dispose()
    val out = ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    return out.toByteArray()
}

private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

private fun freshManager(): Pair<StickerManager, StadeDb> {
    val path = File(
        System.getProperty("java.io.tmpdir"),
        "stade-stickers-${Random.nextLong()}/stade.db"
    ).absolutePath
    val driver = DriverFactory().create(path, "sticker-test-key")
    val db = StadeDb(driver)
    return StickerManager(db, platformCrypto()) to db
}

class StickerImportEndToEndTest {

    private val owner = "owner-1"

    @Test
    fun aPackFileLandsInTheCollectionAsAPack() {
        val (manager, db) = freshManager()
        val archive = zipOf(
            "identifier" to "Holiday".encodeToByteArray(),
            "tray.png" to png(96, 10),
            "a.png" to png(200, 20),
            "b.png" to png(200, 30)
        )
        val result = StickerImporter.importFiles(
            manager, owner, listOf(PickedStickerFile("holiday.wastickers", archive))
        )
        assertEquals(2, result.added)
        assertEquals(listOf("Holiday"), result.packs)

        val packs = db.stadeDbQueries.selectStickerPacks(owner).executeAsList()
        assertEquals(1, packs.size)
        assertEquals("Holiday", packs[0].title)
        val stickers = db.stadeDbQueries.selectStickers(owner).executeAsList()
        assertEquals(2, stickers.size)
        assertTrue(stickers.all { it.packId == packs[0].id })
        assertTrue(stickers.all { it.contentHash.isNotBlank() })
    }

    @Test
    fun theSameStickerIsNeverStoredTwice() {
        val (manager, db) = freshManager()
        val shared = png(200, 40)
        val first = StickerImporter.importFiles(
            manager, owner, listOf(PickedStickerFile("one.png", shared))
        )
        assertEquals(1, first.added)

        val again = StickerImporter.importFiles(
            manager, owner, listOf(PickedStickerFile("one-copy.png", shared))
        )
        assertEquals(0, again.added)
        assertEquals(1, again.duplicates)
        assertEquals(1, db.stadeDbQueries.selectStickers(owner).executeAsList().size)
    }

    @Test
    fun duplicatesInsideOnePackAreCollapsed() {
        val (manager, db) = freshManager()
        val same = png(200, 55)
        val archive = zipOf("a.png" to same, "b.png" to same, "c.png" to png(200, 65))
        val result = StickerImporter.importFiles(
            manager, owner, listOf(PickedStickerFile("dupes.wastickers", archive))
        )
        assertEquals(2, result.added)
        assertEquals(1, result.duplicates)
        assertEquals(2, db.stadeDbQueries.selectStickers(owner).executeAsList().size)
    }

    @Test
    fun looseImagesArriveWithoutAPack() {
        val (manager, db) = freshManager()
        val result = StickerImporter.importFiles(
            manager, owner,
            listOf(PickedStickerFile("x.png", png(180, 70)), PickedStickerFile("y.png", png(180, 80)))
        )
        assertEquals(2, result.added)
        assertTrue(result.packs.isEmpty())
        assertTrue(db.stadeDbQueries.selectStickers(owner).executeAsList().all { it.packId == null })
        assertTrue(db.stadeDbQueries.selectStickerPacks(owner).executeAsList().isEmpty())
    }

    @Test
    fun unreadableFilesAreCountedAndSkipped() {
        val (manager, db) = freshManager()
        val result = StickerImporter.importFiles(
            manager, owner,
            listOf(
                PickedStickerFile("notes.txt", "just text".encodeToByteArray()),
                PickedStickerFile("good.png", png(150, 90))
            )
        )
        assertEquals(1, result.added)
        assertEquals(1, result.unreadable)
        assertEquals(1, db.stadeDbQueries.selectStickers(owner).executeAsList().size)
    }

    @Test
    fun removingAPackTakesItsStickersWithIt() {
        val (manager, db) = freshManager()
        val archive = zipOf("a.png" to png(200, 100), "b.png" to png(200, 110))
        StickerImporter.importFiles(manager, owner, listOf(PickedStickerFile("p.wastickers", archive)))
        StickerImporter.importFiles(manager, owner, listOf(PickedStickerFile("loose.png", png(200, 120))))

        val pack = db.stadeDbQueries.selectStickerPacks(owner).executeAsList().single()
        manager.deletePack(pack.id)

        assertTrue(db.stadeDbQueries.selectStickerPacks(owner).executeAsList().isEmpty())
        val left = db.stadeDbQueries.selectStickers(owner).executeAsList()
        assertEquals(1, left.size, "only the loose sticker should survive")
        assertEquals(null, left[0].packId)
    }

    @Test
    fun oneUsersStickersDoNotBlockAnother() {
        val (manager, db) = freshManager()
        val shared = png(200, 130)
        StickerImporter.importFiles(manager, owner, listOf(PickedStickerFile("a.png", shared)))
        val other = StickerImporter.importFiles(manager, "owner-2", listOf(PickedStickerFile("a.png", shared)))
        assertEquals(1, other.added, "a different account must get its own copy")
        assertEquals(1, db.stadeDbQueries.selectStickers("owner-2").executeAsList().size)
    }
}
