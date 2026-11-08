package dev.stade.security

import dev.stade.db.DriverFactory
import dev.stade.db.StadeDb
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal")

private fun sidecarsBeside(dbFile: File): List<String> =
    SIDECAR_SUFFIXES
        .map { File(dbFile.parentFile, dbFile.name + it) }
        .filter { it.exists() }
        .map { it.name }

private fun journalModeOf(dbPath: String): String =
    DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA journal_mode").use { rows ->
                rows.next()
                rows.getString(1)
            }
        }
    }

private fun fileContains(file: File, needle: ByteArray): Boolean {
    val hay = file.readBytes()
    if (needle.isEmpty() || needle.size > hay.size) return false
    for (i in 0..hay.size - needle.size) {
        var hit = true
        for (j in needle.indices) {
            if (hay[i + j] != needle[j]) {
                hit = false
                break
            }
        }
        if (hit) return true
    }
    return false
}

private fun withRoots(body: (File, File) -> Unit) {
    val a = Files.createTempDirectory("stade-vault-storage")
    val b = Files.createTempDirectory("stade-vault-storage-other")
    try {
        body(a.toFile(), b.toFile())
    } finally {
        a.toFile().deleteRecursively()
        b.toFile().deleteRecursively()
    }
}

class VaultStorageIntegrationTest {

    @Test
    fun theLiveDatabaseKeepsNoPlaintextSidecars() = withRoots { root, _ ->
        val vault = FileVault(root)
        vault.setup("1234")
        val driver = DriverFactory().create(vault.plaintextDbPath())
        val db = StadeDb(driver)
        repeat(50) { db.stadeDbQueries.putKv("probe-$it", "sensitive-$it".encodeToByteArray()) }

        val live = File(vault.plaintextDbPath())
        assertEquals(emptyList(), sidecarsBeside(live), "the live database must stand alone")
        driver.close()
        assertEquals(
            "delete",
            journalModeOf(vault.plaintextDbPath()).lowercase(),
            "write-ahead logging must stay off so no plaintext journal persists"
        )
    }

    @Test
    fun deletedRowsDoNotLingerInTheDatabaseFile() = withRoots { root, _ ->
        val vault = FileVault(root)
        vault.setup("1234")
        val driver = DriverFactory().create(vault.plaintextDbPath())
        val db = StadeDb(driver)
        val secret = "UNIQUE-SECRET-PAYLOAD-7f3a9c".encodeToByteArray()
        db.stadeDbQueries.putKv("probe-secret", secret)

        val live = File(vault.plaintextDbPath())
        assertTrue(fileContains(live, secret), "the row must be in the file before it is deleted")

        db.stadeDbQueries.deleteKv("probe-secret")

        assertFalse(
            fileContains(live, secret),
            "a deleted row must not stay readable in the database file"
        )
        driver.close()
    }

    @Test
    fun realRowsSurviveTheLockAndUnlockRoundTrip() = withRoots { root, _ ->
        val vault = FileVault(root)
        vault.setup("1234")
        val factory = DriverFactory()
        val driver = factory.create(vault.plaintextDbPath())
        StadeDb(driver).stadeDbQueries.putKv("carried", "payload".encodeToByteArray())

        driver.close()
        vault.flushAndClose()

        val live = File(vault.plaintextDbPath())
        assertFalse(live.exists(), "a lock must remove the plaintext database")
        assertEquals(emptyList(), sidecarsBeside(live), "a lock must leave no journal behind")
        assertTrue(File(root, "stade.db.enc").exists())

        val reopened = FileVault(root)
        assertTrue(reopened.unlock("1234") is UnlockOutcome.Success)
        val driver2 = factory.create(reopened.plaintextDbPath())
        val carried = StadeDb(driver2).stadeDbQueries.getKv("carried").executeAsOneOrNull()
        assertEquals("payload", carried?.decodeToString())
        driver2.close()
    }

    @Test
    fun rejectsARealDatabasePlantedAfterACleanLock() = withRoots { root, attacker ->
        val vault = FileVault(root)
        vault.setup("1234")
        val factory = DriverFactory()
        val driver = factory.create(vault.plaintextDbPath())
        StadeDb(driver).stadeDbQueries.putKv("probe", "genuine".encodeToByteArray())
        driver.close()
        vault.flushAndClose()

        val plantedSource = File(attacker, "stade.db")
        val evil = factory.create(plantedSource.absolutePath)
        StadeDb(evil).stadeDbQueries.putKv("probe", "attacker".encodeToByteArray())
        evil.close()
        plantedSource.copyTo(File(vault.plaintextDbPath()), overwrite = true)

        val victim = FileVault(root)
        assertTrue(victim.unlock("1234") is UnlockOutcome.Success, "the vault must still open")
        val driver2 = factory.create(victim.plaintextDbPath())
        val value = StadeDb(driver2).stadeDbQueries.getKv("probe").executeAsOneOrNull()
        assertEquals(
            "genuine",
            value?.decodeToString(),
            "a database planted after a clean lock must not replace the authenticated copy"
        )
        driver2.close()
    }
}
