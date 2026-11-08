package dev.stade.backup

import dev.stade.security.Vault
import dev.stade.security.torIdentityPath
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class BackupTargets(
    val vaultMetaPath: String,
    val encryptedDbPath: String,
    val plaintextDbPath: String,
    val onionKeyPath: String
)

enum class RestoreFailure { WrongPassphrase, NotABackup, Damaged, Incomplete, WriteFailed, NewerVersion }

private val DB_SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal")

sealed interface VerifyOutcome {
    data class Valid(val entries: List<BackupEntry>, val payloadBytes: Long, val onionKeyIncluded: Boolean) : VerifyOutcome
    data class Failed(val reason: RestoreFailure) : VerifyOutcome
}

sealed interface RestoreOutcome {
    data class Restored(val onionKeyIncluded: Boolean) : RestoreOutcome
    data class Failed(val reason: RestoreFailure) : RestoreOutcome
}

object StadeBackup {

    const val ENTRY_VAULT = "vault"
    const val ENTRY_DB = "db"
    const val ENTRY_ONION = "onion"

    fun targetsFor(vault: Vault): BackupTargets = BackupTargets(
        vaultMetaPath = vault.metaPath(),
        encryptedDbPath = vault.encryptedDbPath(),
        plaintextDbPath = vault.plaintextDbPath(),
        onionKeyPath = torIdentityPath()
    )

    fun export(out: OutputStream, passphrase: CharArray, targets: BackupTargets): Boolean {
        val meta = File(targets.vaultMetaPath)
        val db = File(targets.encryptedDbPath)
        if (!meta.isFile || !db.isFile) return false

        val sources = mutableListOf(
            BackupSource(ENTRY_VAULT, meta.length()) { meta.inputStream() },
            BackupSource(ENTRY_DB, db.length()) { db.inputStream() }
        )
        val onion = File(targets.onionKeyPath)
        if (onion.isFile) {
            sources.add(BackupSource(ENTRY_ONION, onion.length()) { onion.inputStream() })
        }
        BackupArchive.write(out, passphrase, sources)
        return true
    }

    fun verify(input: InputStream, passphrase: CharArray): VerifyOutcome {
        val outcome = BackupArchive.verify(input, passphrase)
        val ok = when (outcome) {
            is BackupVerifyOutcome.Ok -> outcome
            BackupVerifyOutcome.WrongPassphrase -> return VerifyOutcome.Failed(RestoreFailure.WrongPassphrase)
            BackupVerifyOutcome.NotABackup -> return VerifyOutcome.Failed(RestoreFailure.NotABackup)
            BackupVerifyOutcome.Damaged -> return VerifyOutcome.Failed(RestoreFailure.Damaged)
            BackupVerifyOutcome.NewerVersion -> return VerifyOutcome.Failed(RestoreFailure.NewerVersion)
        }
        val byName = ok.entries.associateBy { it.name }
        if (byName[ENTRY_VAULT] == null || byName[ENTRY_DB] == null) {
            return VerifyOutcome.Failed(RestoreFailure.Incomplete)
        }
        return VerifyOutcome.Valid(
            entries = ok.entries,
            payloadBytes = ok.payloadBytes,
            onionKeyIncluded = byName[ENTRY_ONION] != null
        )
    }

    fun restore(
        input: InputStream,
        passphrase: CharArray,
        targets: BackupTargets,
        scratch: File
    ): RestoreOutcome {
        val outcome = BackupArchive.read(input, passphrase, scratch)
        val ok = when (outcome) {
            is BackupReadOutcome.Ok -> outcome
            BackupReadOutcome.WrongPassphrase -> return RestoreOutcome.Failed(RestoreFailure.WrongPassphrase)
            BackupReadOutcome.NotABackup -> return RestoreOutcome.Failed(RestoreFailure.NotABackup)
            BackupReadOutcome.Damaged -> return RestoreOutcome.Failed(RestoreFailure.Damaged)
            BackupReadOutcome.NewerVersion -> return RestoreOutcome.Failed(RestoreFailure.NewerVersion)
        }

        try {
            val byName = ok.entries.associateBy { it.name }
            val meta = byName[ENTRY_VAULT]
            val db = byName[ENTRY_DB]
            if (meta == null || db == null) {
                return RestoreOutcome.Failed(RestoreFailure.Incomplete)
            }

            val staged = mutableListOf<Pair<File, File>>()
            val metaTmp = stage(ok.payload, meta, File(targets.vaultMetaPath))
            staged.add(metaTmp to File(targets.vaultMetaPath))
            val dbTmp = stage(ok.payload, db, File(targets.encryptedDbPath))
            staged.add(dbTmp to File(targets.encryptedDbPath))

            val onion = byName[ENTRY_ONION]
            var onionTmp: File? = null
            if (onion != null) {
                File(targets.onionKeyPath).parentFile?.mkdirs()
                onionTmp = stage(ok.payload, onion, File(targets.onionKeyPath))
                staged.add(onionTmp to File(targets.onionKeyPath))
            }

            val displaced = mutableListOf<Pair<File, File>>()
            var promoted = true
            for ((tmp, dest) in staged) {
                if (dest.exists()) {
                    val aside = File(dest.parentFile, dest.name + ".prerestore")
                    runCatching { aside.delete() }
                    val movedAside = dest.renameTo(aside) || runCatching {
                        dest.copyTo(aside, overwrite = true)
                        dest.delete()
                    }.getOrDefault(false)
                    if (!movedAside) {
                        promoted = false
                        break
                    }
                    displaced.add(dest to aside)
                }
                if (!promote(tmp, dest)) {
                    promoted = false
                    break
                }
            }

            if (!promoted) {
                displaced.forEach { (dest, aside) ->
                    runCatching {
                        dest.delete()
                        if (!aside.renameTo(dest)) aside.copyTo(dest, overwrite = true)
                    }
                }
                staged.forEach { (tmp, _) -> runCatching { tmp.delete() } }
                return RestoreOutcome.Failed(RestoreFailure.WriteFailed)
            }

            val live = File(targets.plaintextDbPath)
            val plaintextRemoved = runCatching {
                live.delete()
                DB_SIDECAR_SUFFIXES.forEach { File(live.parentFile, live.name + it).delete() }
                !live.exists()
            }.getOrDefault(false)
            if (!plaintextRemoved) {
                displaced.forEach { (dest, aside) ->
                    runCatching {
                        dest.delete()
                        if (!aside.renameTo(dest)) aside.copyTo(dest, overwrite = true)
                    }
                }
                return RestoreOutcome.Failed(RestoreFailure.WriteFailed)
            }

            displaced.forEach { (_, aside) -> runCatching { aside.delete() } }
            onionTmp?.let { restrictPermissions(File(targets.onionKeyPath)) }
            return RestoreOutcome.Restored(onionKeyIncluded = onion != null)
        } catch (_: Exception) {
            return RestoreOutcome.Failed(RestoreFailure.WriteFailed)
        } finally {
            runCatching { ok.payload.delete() }
        }
    }

    private fun stage(payload: File, entry: BackupEntry, dest: File): File {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".restore")
        payload.inputStream().use { source ->
            var skipped = 0L
            while (skipped < entry.offset) {
                val n = source.skip(entry.offset - skipped)
                if (n <= 0) throw IllegalStateException("payload truncated")
                skipped += n
            }
            tmp.outputStream().use { sink ->
                val buffer = ByteArray(64 * 1024)
                var remaining = entry.length
                while (remaining > 0) {
                    val n = source.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                    if (n <= 0) throw IllegalStateException("payload truncated")
                    sink.write(buffer, 0, n)
                    remaining -= n
                }
            }
        }
        return tmp
    }

    private fun promote(tmp: File, dest: File): Boolean {
        if (tmp.renameTo(dest)) return true
        if (dest.exists() && !dest.delete()) return false
        if (tmp.renameTo(dest)) return true
        return runCatching {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
            true
        }.getOrDefault(false)
    }

    private fun restrictPermissions(file: File) {
        runCatching {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
        }
    }
}
