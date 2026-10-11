package dev.stade.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import dev.stade.backup.RestoreFailure
import dev.stade.backup.RestoreOutcome
import dev.stade.backup.StadeBackup
import dev.stade.backup.VerifyOutcome
import dev.stade.security.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual class BackupIoLauncher(
    private val onExport: (String) -> Unit,
    private val onImport: (String) -> Unit,
    private val onVerify: (String) -> Unit
) {
    actual fun exportBackup(passphrase: String) = onExport(passphrase)
    actual fun importBackup(passphrase: String) = onImport(passphrase)
    actual fun verifyBackup(passphrase: String) = onVerify(passphrase)
}

private fun chooseFile(save: Boolean, suggested: String?): File? {
    val dialog = FileDialog(null as Frame?, "Stade", if (save) FileDialog.SAVE else FileDialog.LOAD)
    if (suggested != null) dialog.file = suggested
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}

@Composable
actual fun rememberBackupIo(
    vault: Vault,
    onOutcome: (BackupOutcome) -> Unit
): BackupIoLauncher {
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onOutcome)

    return remember(vault) {
        BackupIoLauncher(
            onExport = { passphrase ->
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        val target = chooseFile(save = true, suggested = defaultBackupFileName())
                            ?: return@withContext BackupOutcome.Cancelled
                        runCatching {
                            vault.withFlushLock {
                                vault.flushAndKeep()
                                target.outputStream().use { out ->
                                    StadeBackup.export(
                                        out,
                                        passphrase.toCharArray(),
                                        StadeBackup.targetsFor(vault)
                                    )
                                }
                            }
                        }.getOrDefault(false).let {
                            if (it) {
                                BackupOutcome.Exported
                            } else {
                                runCatching { target.delete() }
                                BackupOutcome.Failed
                            }
                        }
                    }
                    callback(outcome)
                }
            },
            onImport = { passphrase ->
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        val source = chooseFile(save = false, suggested = null)
                            ?: return@withContext BackupOutcome.Cancelled
                        val scratch = File.createTempFile("stade-restore", ".tmp")
                        try {
                            runCatching {
                                source.inputStream().use { input ->
                                    StadeBackup.restore(
                                        input,
                                        passphrase.toCharArray(),
                                        StadeBackup.targetsFor(vault),
                                        scratch
                                    )
                                }
                            }.getOrNull().toBackupOutcome()
                        } finally {
                            runCatching { scratch.delete() }
                        }
                    }
                    callback(outcome)
                }
            },
            onVerify = { passphrase ->
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        val source = chooseFile(save = false, suggested = null)
                            ?: return@withContext BackupOutcome.Cancelled
                        runCatching {
                            source.inputStream().use { input ->
                                StadeBackup.verify(input, passphrase.toCharArray())
                            }
                        }.getOrNull().toBackupOutcome()
                    }
                    callback(outcome)
                }
            }
        )
    }
}

internal fun RestoreOutcome?.toBackupOutcome(): BackupOutcome = when (this) {
    null -> BackupOutcome.Failed
    is RestoreOutcome.Restored -> BackupOutcome.Restored(onionKeyIncluded)
    is RestoreOutcome.Failed -> when (reason) {
        RestoreFailure.WrongPassphrase -> BackupOutcome.WrongPassphrase
        RestoreFailure.NotABackup -> BackupOutcome.NotABackup
        RestoreFailure.Damaged -> BackupOutcome.Damaged
        RestoreFailure.Incomplete -> BackupOutcome.Damaged
        RestoreFailure.WriteFailed -> BackupOutcome.Failed
        RestoreFailure.NewerVersion -> BackupOutcome.NewerVersion
    }
}

internal fun VerifyOutcome?.toBackupOutcome(): BackupOutcome = when (this) {
    null -> BackupOutcome.Failed
    is VerifyOutcome.Valid -> BackupOutcome.Verified(onionKeyIncluded, payloadBytes)
    is VerifyOutcome.Failed -> when (reason) {
        RestoreFailure.WrongPassphrase -> BackupOutcome.WrongPassphrase
        RestoreFailure.NotABackup -> BackupOutcome.NotABackup
        RestoreFailure.Damaged -> BackupOutcome.Damaged
        RestoreFailure.Incomplete -> BackupOutcome.Damaged
        RestoreFailure.WriteFailed -> BackupOutcome.Failed
        RestoreFailure.NewerVersion -> BackupOutcome.NewerVersion
    }
}
