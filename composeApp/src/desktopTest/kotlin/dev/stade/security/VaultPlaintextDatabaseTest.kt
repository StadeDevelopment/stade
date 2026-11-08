package dev.stade.security

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun withVaultRoot(body: (File) -> Unit) {
    val root = Files.createTempDirectory("stade-vault-plaintext")
    try {
        body(root.toFile())
    } finally {
        root.toFile().deleteRecursively()
    }
}

class VaultPlaintextDatabaseTest {

    @Test
    fun discardsAPlantedDatabaseAfterACleanLock() = withVaultRoot { root ->
        val vault = FileVault(root)
        vault.setup("1234")
        val plaintext = File(root, "stade.db")
        plaintext.writeBytes(sqliteFixture("genuine"))
        vault.flushAndClose()
        assertFalse(plaintext.exists(), "a clean lock must remove the plaintext database")

        plaintext.writeBytes(sqliteFixture("planted"))

        val reopened = FileVault(root)
        assertTrue(reopened.unlock("1234") is UnlockOutcome.Success)
        assertEquals(
            "genuine",
            sqliteFixtureMarker(plaintext.readBytes()),
            "a database that appeared after a clean lock must not be adopted"
        )
    }

    @Test
    fun keepsTheLiveDatabaseAfterAnUncleanShutdown() = withVaultRoot { root ->
        val vault = FileVault(root)
        vault.setup("1234")
        val plaintext = File(root, "stade.db")
        plaintext.writeBytes(sqliteFixture("snapshotted"))
        vault.flushAndKeep()

        plaintext.writeBytes(sqliteFixture("newer-than-snapshot"))

        val reopened = FileVault(root)
        assertTrue(reopened.unlock("1234") is UnlockOutcome.Success)
        assertEquals(
            "newer-than-snapshot",
            sqliteFixtureMarker(plaintext.readBytes()),
            "a crash must not roll the database back to the last snapshot"
        )
    }

    @Test
    fun rejectsAPlantedFileThatOnlyLooksLikeADatabase() = withVaultRoot { root ->
        val vault = FileVault(root)
        vault.setup("1234")
        val plaintext = File(root, "stade.db")
        plaintext.writeBytes(sqliteFixture("genuine"))
        vault.flushAndKeep()

        plaintext.writeBytes("SQLite format 3\u0000truncated".encodeToByteArray())

        val reopened = FileVault(root)
        assertTrue(reopened.unlock("1234") is UnlockOutcome.Success)
        assertEquals(
            "genuine",
            sqliteFixtureMarker(plaintext.readBytes()),
            "the magic bytes alone must not qualify a file as the live database"
        )
    }

    @Test
    fun removesJournalSidecarsOnLock() = withVaultRoot { root ->
        val vault = FileVault(root)
        vault.setup("1234")
        File(root, "stade.db").writeBytes(sqliteFixture("genuine"))
        val wal = File(root, "stade.db-wal").apply { writeText("secret pages") }
        val shm = File(root, "stade.db-shm").apply { writeText("shared index") }
        val journal = File(root, "stade.db-journal").apply { writeText("rollback pages") }

        vault.flushAndClose()

        assertFalse(wal.exists(), "the write-ahead log must not survive a lock")
        assertFalse(shm.exists(), "the shared-memory index must not survive a lock")
        assertFalse(journal.exists(), "the rollback journal must not survive a lock")
    }

    @Test
    fun removesJournalSidecarsOnWipe() = withVaultRoot { root ->
        val vault = FileVault(root)
        vault.setup("1234")
        File(root, "stade.db").writeBytes(sqliteFixture("genuine"))
        val wal = File(root, "stade.db-wal").apply { writeText("secret pages") }
        val journal = File(root, "stade.db-journal").apply { writeText("rollback pages") }

        vault.wipe()

        assertFalse(wal.exists(), "a wipe must clear the write-ahead log")
        assertFalse(journal.exists(), "a wipe must clear the rollback journal")
        assertFalse(File(root, "stade.db").exists())
        assertFalse(File(root, "stade.vault").exists())
    }

    @Test
    fun discardsStaleSidecarsWhenRestoringFromTheEncryptedCopy() = withVaultRoot { root ->
        val vault = FileVault(root)
        vault.setup("1234")
        val plaintext = File(root, "stade.db")
        plaintext.writeBytes(sqliteFixture("genuine"))
        vault.flushAndClose()
        val wal = File(root, "stade.db-wal").apply { writeText("stale pages") }

        val reopened = FileVault(root)
        assertTrue(reopened.unlock("1234") is UnlockOutcome.Success)

        assertFalse(wal.exists(), "a restored database must not be paired with a stale journal")
        assertEquals("genuine", sqliteFixtureMarker(plaintext.readBytes()))
    }

    @Test
    fun readsMetaWrittenBeforeTheCleanShutdownFlagExisted() = withVaultRoot { root ->
        val vault = FileVault(root)
        vault.setup("1234")
        File(root, "stade.db").writeBytes(sqliteFixture("genuine"))
        vault.flushAndClose()

        val meta = File(root, "stade.vault")
        val legacy = meta.readBytes().copyOfRange(0, meta.readBytes().size - 1)
        RandomAccessFile(meta, "rw").use { it.setLength(legacy.size.toLong()) }
        assertContentEquals(legacy, meta.readBytes())

        val reopened = FileVault(root)
        assertTrue(
            reopened.unlock("1234") is UnlockOutcome.Success,
            "an older vault file without the flag must still open"
        )
        assertEquals("genuine", sqliteFixtureMarker(File(root, "stade.db").readBytes()))
    }
}
