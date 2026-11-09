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

class VaultSidecarAndMetaTest {




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
    fun readsMetaWrittenBeforeTheCleanShutdownFlagExisted() = withVaultRoot { root ->
        val vault = FileVault(root)
        vault.setup("1234")
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
        assertTrue(reopened.databaseKey() != null, "and must hand back a usable database key")
    }
}
