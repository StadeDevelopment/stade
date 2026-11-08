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
        encryptedDbPath = File(root, "stade.db.enc").absolutePath,
        plaintextDbPath = File(root, "stade.db").absolutePath,
        onionKeyPath = File(root, "tor/onion.key").absolutePath
    )

    private fun archiveOf(meta: String, db: String, passphrase: String): ByteArray {
        val root = newRoot()
        try {
            val targets = targetsIn(root)
            File(targets.vaultMetaPath).writeBytes(meta.encodeToByteArray())
            File(targets.encryptedDbPath).writeBytes(db.encodeToByteArray())
            val out = ByteArrayOutputStream()
            assertTrue(StadeBackup.export(out, passphrase.toCharArray(), targets), "fixture export")
            return out.toByteArray()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aRestoreThatCannotFinishLeavesTheOldInstallIntact() {
        val root = newRoot()
        try {
            val targets = targetsIn(root)
            File(targets.vaultMetaPath).writeBytes("ORIGINAL-META".encodeToByteArray())
            File(targets.encryptedDbPath).writeBytes("ORIGINAL-DB".encodeToByteArray())
            val archive = archiveOf("BACKUP-META", "BACKUP-DB", "pass")

            File(targets.plaintextDbPath).mkdirs()
            File(File(targets.plaintextDbPath), "blocker").writeText("x")

            val scratch = File(root, "scratch")
            val outcome = StadeBackup.restore(
                ByteArrayInputStream(archive), "pass".toCharArray(), targets, scratch
            )

            assertTrue(outcome is RestoreOutcome.Failed, "the restore must report failure: $outcome")
            assertEquals(
                "ORIGINAL-META",
                File(targets.vaultMetaPath).readBytes().decodeToString(),
                "a failed restore must not leave the backup's vault key over the old database"
            )
            assertEquals(
                "ORIGINAL-DB",
                File(targets.encryptedDbPath).readBytes().decodeToString(),
                "a failed restore must leave the old encrypted database in place"
            )
            assertFalse(
                File(root, "stade.vault.prerestore").exists(),
                "rollback must not leave its working copies behind"
            )
            assertFalse(
                File(root, "stade.db.enc.prerestore").exists(),
                "rollback must not leave its working copies behind"
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aCleanRestoreStillReplacesBothFilesTogether() {
        val root = newRoot()
        try {
            val targets = targetsIn(root)
            File(targets.vaultMetaPath).writeBytes("ORIGINAL-META".encodeToByteArray())
            File(targets.encryptedDbPath).writeBytes("ORIGINAL-DB".encodeToByteArray())
            File(targets.plaintextDbPath).writeBytes("live".encodeToByteArray())
            val archive = archiveOf("BACKUP-META", "BACKUP-DB", "pass")

            val outcome = StadeBackup.restore(
                ByteArrayInputStream(archive), "pass".toCharArray(), targets, File(root, "scratch")
            )

            assertTrue(outcome is RestoreOutcome.Restored, "the restore must succeed: $outcome")
            assertEquals("BACKUP-META", File(targets.vaultMetaPath).readBytes().decodeToString())
            assertEquals("BACKUP-DB", File(targets.encryptedDbPath).readBytes().decodeToString())
            assertFalse(File(targets.plaintextDbPath).exists(), "the stale live database must be gone")
            assertFalse(File(root, "stade.vault.prerestore").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
