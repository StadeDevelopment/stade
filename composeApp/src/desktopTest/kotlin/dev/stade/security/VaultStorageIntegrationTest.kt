package dev.stade.security

import dev.stade.db.DriverFactory
import dev.stade.db.StadeDb
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal")

private fun sidecarsBeside(dbFile: File): List<String> =
    SIDECAR_SUFFIXES
        .map { File(dbFile.parentFile, dbFile.name + it) }
        .filter { it.exists() }
        .map { it.name }

private fun fileContains(file: File, needle: ByteArray): Boolean {
    if (!file.exists()) return false
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

private fun withRoot(body: (File) -> Unit) {
    val dir = Files.createTempDirectory("stade-vault-storage")
    try {
        body(dir.toFile())
    } finally {
        dir.toFile().deleteRecursively()
    }
}

class VaultStorageIntegrationTest {

    private val secret = "UNIQUE-SECRET-PAYLOAD-7f3a9c".encodeToByteArray()

    @Test
    fun theDatabaseOnDiskIsNotReadableWhileTheAppIsRunning() = withRoot { root ->
        val vault = FileVault(root)
        vault.setup("123456")
        val key = assertNotNull(vault.databaseKey(), "an unlocked vault must expose a database key")
        val driver = DriverFactory().create(vault.databasePath(), key)
        StadeDb(driver).stadeDbQueries.putKv("probe", secret)

        val live = File(vault.databasePath())
        assertTrue(live.exists() && live.length() > 0, "the database must exist on disk")
        assertFalse(
            fileContains(live, secret),
            "a row must not be readable in the database file even while the app is unlocked"
        )
        assertFalse(
            live.readBytes().copyOfRange(0, 16).decodeToString().startsWith("SQLite format 3"),
            "the file must not be a plain database at all"
        )
        driver.close()
    }

    @Test
    fun aCrashLeavesNothingReadableBehind() = withRoot { root ->
        val vault = FileVault(root)
        vault.setup("123456")
        val driver = DriverFactory().create(vault.databasePath(), vault.databaseKey()!!)
        StadeDb(driver).stadeDbQueries.putKv("probe", secret)
        driver.close()

        val live = File(vault.databasePath())
        assertFalse(
            fileContains(live, secret),
            "a device seized after a crash must not yield the contents"
        )
    }

    @Test
    fun lockingClearsTheKeyAndTheJournals() = withRoot { root ->
        val vault = FileVault(root)
        vault.setup("123456")
        val driver = DriverFactory().create(vault.databasePath(), vault.databaseKey()!!)
        StadeDb(driver).stadeDbQueries.putKv("probe", secret)
        driver.close()

        vault.flushAndClose()

        assertFalse(vault.isUnlocked())
        assertEquals(null, vault.databaseKey(), "a locked vault must not hand out the database key")
        assertEquals(emptyList(), sidecarsBeside(File(vault.databasePath())))
    }

    @Test
    fun realRowsSurviveLockAndUnlock() = withRoot { root ->
        val vault = FileVault(root)
        vault.setup("123456")
        val factory = DriverFactory()
        val first = factory.create(vault.databasePath(), vault.databaseKey()!!)
        StadeDb(first).stadeDbQueries.putKv("carried", "payload".encodeToByteArray())
        first.close()
        vault.flushAndClose()

        val reopened = FileVault(root)
        assertTrue(reopened.unlock("123456") is UnlockOutcome.Success)
        val second = factory.create(reopened.databasePath(), reopened.databaseKey()!!)
        assertEquals(
            "payload",
            StadeDb(second).stadeDbQueries.getKv("carried").executeAsOneOrNull()?.decodeToString()
        )
        second.close()
    }

    @Test
    fun theDatabaseCannotBeOpenedWithAnotherVaultsKey() = withRoot { root ->
        val vault = FileVault(root)
        vault.setup("123456")
        val factory = DriverFactory()
        val driver = factory.create(vault.databasePath(), vault.databaseKey()!!)
        StadeDb(driver).stadeDbQueries.putKv("probe", secret)
        driver.close()

        val outcome = runCatching {
            val wrong = factory.create(vault.databasePath(), "0".repeat(64))
            StadeDb(wrong).stadeDbQueries.getKv("probe").executeAsOneOrNull()
        }

        assertTrue(
            outcome.isFailure,
            "the database must be useless to anyone who does not have the vault key"
        )
    }

    @Test
    fun aVaultFromTheOldFormatSaysSoInsteadOfLookingEmpty() = withRoot { root ->
        val vault = FileVault(root)
        vault.setup("123456")
        vault.flushAndClose()
        File(root, "stade.db").delete()
        File(root, "stade.db.enc").writeBytes("STDD".encodeToByteArray() + ByteArray(64))

        val reopened = FileVault(root)
        val outcome = reopened.unlock("123456")

        assertTrue(
            outcome is UnlockOutcome.Error,
            "an older vault must be reported, not silently replaced by an empty one"
        )
        assertTrue(reopened.hasLegacyEncryptedDatabase())
    }
}
