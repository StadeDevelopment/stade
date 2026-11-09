package dev.stade.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestoreRollbackTest {

    private fun newRoot(): File = File.createTempFile("stade-restore", "").let {
        it.delete()
        it.mkdirs()
        it
    }

    private fun targetsIn(root: File) = BackupTargets(
        vaultMetaPath = File(root, "stade.vault").absolutePath,
        databasePath = File(root, "stade.db").absolutePath,
        onionKeyPath = File(root, "tor/onion.key").absolutePath
    )

    private fun archiveOf(meta: String, db: String, passphrase: String): ByteArray {
        val root = newRoot()
        try {
            val targets = targetsIn(root)
            File(targets.vaultMetaPath).writeBytes(meta.encodeToByteArray())
            File(targets.databasePath).writeBytes(db.encodeToByteArray())
            val out = ByteArrayOutputStream()
            assertTrue(StadeBackup.export(out, passphrase.toCharArray(), targets), "fixture export")
            return out.toByteArray()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aRestoreThatCannotFinishLeavesTheOldVaultKeyInPlace() {
        val root = newRoot()
        try {
            val targets = targetsIn(root)
            File(targets.vaultMetaPath).writeBytes("ORIGINAL-META".encodeToByteArray())
            val archive = archiveOf("BACKUP-META", "BACKUP-DB", "pass")

            File(targets.databasePath).mkdirs()
            File(File(targets.databasePath), "blocker").writeText("x")
            File(root, "stade.db.prerestore").mkdirs()
            File(File(root, "stade.db.prerestore"), "blocker").writeText("x")

            val outcome = StadeBackup.restore(
                ByteArrayInputStream(archive), "pass".toCharArray(), targets, File(root, "scratch")
            )

            assertTrue(outcome is RestoreOutcome.Failed, "the restore must report failure: $outcome")
            assertEquals(
                "ORIGINAL-META",
                File(targets.vaultMetaPath).readBytes().decodeToString(),
                "a failed restore must not leave the backup's vault key over a database it could not replace"
            )
            assertFalse(
                File(root, "stade.vault.prerestore").exists(),
                "rollback must not leave its working copy behind"
            )
            assertFalse(
                File(root, "stade.vault.restore").exists(),
                "a failed restore must not leave staged files behind"
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aCleanRestoreReplacesTheKeyAndTheDatabaseTogether() {
        val root = newRoot()
        try {
            val targets = targetsIn(root)
            File(targets.vaultMetaPath).writeBytes("ORIGINAL-META".encodeToByteArray())
            File(targets.databasePath).writeBytes("ORIGINAL-DB".encodeToByteArray())
            File(root, "stade.db-wal").writeText("stale journal")
            val archive = archiveOf("BACKUP-META", "BACKUP-DB", "pass")

            val outcome = StadeBackup.restore(
                ByteArrayInputStream(archive), "pass".toCharArray(), targets, File(root, "scratch")
            )

            assertTrue(outcome is RestoreOutcome.Restored, "the restore must succeed: $outcome")
            assertEquals("BACKUP-META", File(targets.vaultMetaPath).readBytes().decodeToString())
            assertEquals("BACKUP-DB", File(targets.databasePath).readBytes().decodeToString())
            assertFalse(
                File(root, "stade.db-wal").exists(),
                "a journal from the replaced database must not be paired with the restored one"
            )
            assertFalse(File(root, "stade.vault.prerestore").exists())
            assertFalse(File(root, "stade.db.prerestore").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
