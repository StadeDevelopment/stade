package dev.stade.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

private class Install(val root: File) {
    val targets = BackupTargets(
        vaultMetaPath = File(root, "stade.vault").absolutePath,
        databasePath = File(root, "stade.db.enc").absolutePath,
        onionKeyPath = File(root, "tor/hs/hs_ed25519_secret_key").absolutePath
    )

    fun seed(dbBytes: ByteArray) {
        root.mkdirs()
        File(targets.vaultMetaPath).writeBytes(Random(1).nextBytes(196))
        File(targets.databasePath).writeBytes(dbBytes)
        File(targets.onionKeyPath).apply { parentFile.mkdirs() }.writeBytes(Random(2).nextBytes(96))
    }
}

private fun tempRoot(name: String): File =
    File(System.getProperty("java.io.tmpdir"), "stade-$name-${Random.nextLong()}").apply { mkdirs() }

class BackupRealisticSizeTest {

    @Test
    fun aMultiMegabyteAccountSurvivesExportAndRestore() {
        val source = Install(tempRoot("src"))
        val db = Random(7).nextBytes(8 * 1024 * 1024 + 12_345)
        source.seed(db)

        val out = ByteArrayOutputStream()
        assertTrue(StadeBackup.export(out, "a-long-enough-passphrase".toCharArray(), source.targets))

        val target = Install(tempRoot("dst"))
        target.root.mkdirs()
        val scratch = File(tempRoot("scratch"), "payload.tmp")
        val outcome = StadeBackup.restore(
            ByteArrayInputStream(out.toByteArray()),
            "a-long-enough-passphrase".toCharArray(),
            target.targets,
            scratch
        )
        assertTrue(outcome is RestoreOutcome.Restored, "expected a restore, got $outcome")
        assertContentEquals(db, File(target.targets.databasePath).readBytes())
    }

    @Test
    fun aDatabaseThatGrowsWhileTheBackupIsWrittenStillRestores() {
        val source = Install(tempRoot("grow"))
        val db = Random(8).nextBytes(3 * 1024 * 1024)
        source.seed(db)

        val meta = File(source.targets.vaultMetaPath)
        val encrypted = File(source.targets.databasePath)
        val onion = File(source.targets.onionKeyPath)
        val sources = listOf(
            BackupSource(StadeBackup.ENTRY_VAULT, meta.length()) { meta.inputStream() },
            BackupSource(StadeBackup.ENTRY_DB, encrypted.length()) {
                encrypted.appendBytes(Random(9).nextBytes(64 * 1024))
                encrypted.inputStream()
            },
            BackupSource(StadeBackup.ENTRY_ONION, onion.length()) { onion.inputStream() }
        )

        val out = ByteArrayOutputStream()
        BackupArchive.write(out, "a-long-enough-passphrase".toCharArray(), sources)

        val target = Install(tempRoot("grown"))
        target.root.mkdirs()
        val scratch = File(tempRoot("scratch2"), "payload.tmp")
        val outcome = StadeBackup.restore(
            ByteArrayInputStream(out.toByteArray()),
            "a-long-enough-passphrase".toCharArray(),
            target.targets,
            scratch
        )
        assertTrue(outcome is RestoreOutcome.Restored, "expected a restore, got $outcome")
        assertEquals(db.size.toLong(), File(target.targets.databasePath).length())
    }

    @Test
    fun anEntryLandingExactlyOnAChunkBoundarySurvives() {
        val source = Install(tempRoot("boundary"))
        val header = 2 + StadeBackup.ENTRY_VAULT.length + 8
        val metaLen = BackupArchive.CHUNK_SIZE - header - (2 + StadeBackup.ENTRY_DB.length + 8)
        source.root.mkdirs()
        File(source.targets.vaultMetaPath).writeBytes(Random(3).nextBytes(metaLen))
        val db = Random(4).nextBytes(BackupArchive.CHUNK_SIZE * 2)
        File(source.targets.databasePath).writeBytes(db)

        val out = ByteArrayOutputStream()
        assertTrue(StadeBackup.export(out, "a-long-enough-passphrase".toCharArray(), source.targets))

        val target = Install(tempRoot("boundary-dst"))
        target.root.mkdirs()
        val scratch = File(tempRoot("scratch3"), "payload.tmp")
        val outcome = StadeBackup.restore(
            ByteArrayInputStream(out.toByteArray()),
            "a-long-enough-passphrase".toCharArray(),
            target.targets,
            scratch
        )
        assertTrue(outcome is RestoreOutcome.Restored, "expected a restore, got $outcome")
        assertContentEquals(db, File(target.targets.databasePath).readBytes())
    }

    @Test
    fun aBackupIsNeverWrittenPartiallyWhenTheSourceShrinksMidStream() {
        val source = Install(tempRoot("shrink"))
        val db = Random(21).nextBytes(4 * 1024 * 1024)
        source.seed(db)

        val meta = File(source.targets.vaultMetaPath)
        val encrypted = File(source.targets.databasePath)
        val declared = encrypted.length()
        val sources = listOf(
            BackupSource(StadeBackup.ENTRY_VAULT, meta.length()) { meta.inputStream() },
            BackupSource(StadeBackup.ENTRY_DB, declared) {
                encrypted.writeBytes(Random(22).nextBytes(1024))
                encrypted.inputStream()
            }
        )

        val out = ByteArrayOutputStream()
        val threw = runCatching {
            BackupArchive.write(out, "a-long-enough-passphrase".toCharArray(), sources)
        }.isFailure
        assertTrue(threw, "a shrinking source must abort the export")

        val target = Install(tempRoot("shrink-dst"))
        target.root.mkdirs()
        val scratch = File(tempRoot("scratch4"), "payload.tmp")
        val outcome = StadeBackup.restore(
            ByteArrayInputStream(out.toByteArray()),
            "a-long-enough-passphrase".toCharArray(),
            target.targets,
            scratch
        )
        assertTrue(
            outcome is RestoreOutcome.Failed,
            "the half-written archive must not look restorable, got $outcome"
        )
    }

    @Test
    fun theEncryptedDatabaseIsNeverMissingWhileItIsSwappedOut() {
        val root = tempRoot("swap")
        val dest = File(root, "stade.db.enc")
        dest.writeBytes(Random(31).nextBytes(4096))
        val tmp = File(root, "stade.db.enc.tmp")
        tmp.writeBytes(Random(32).nextBytes(8192))
        assertTrue(tmp.renameTo(dest), "rename must replace the target in one step")
        assertTrue(dest.isFile)
        assertEquals(8192L, dest.length())
    }
}
