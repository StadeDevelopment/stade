package dev.stade.ui

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import dev.stade.backup.RestoreFailure
import dev.stade.backup.RestoreOutcome
import dev.stade.backup.StadeBackup
import dev.stade.backup.VerifyOutcome
import dev.stade.security.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

@Composable
actual fun rememberBackupIo(
    vault: Vault,
    onOutcome: (BackupOutcome) -> Unit
): BackupIoLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onOutcome)
    val pending = remember { arrayOfNulls<String>(1) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val passphrase = pending[0]
        pending[0] = null
        if (uri == null || passphrase == null) {
            callback(BackupOutcome.Cancelled)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    vault.withFlushLock {
                        vault.flushAndKeep()
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            StadeBackup.export(out, passphrase.toCharArray(), StadeBackup.targetsFor(vault))
                        } ?: false
                    }
                }.getOrDefault(false)
            }
            if (!ok) {
                withContext(Dispatchers.IO) {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                }
            }
            callback(if (ok) BackupOutcome.Exported else BackupOutcome.Failed)
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val passphrase = pending[0]
        pending[0] = null
        if (uri == null || passphrase == null) {
            callback(BackupOutcome.Cancelled)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val scratch = File(context.cacheDir, "stade-restore.tmp")
                try {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            StadeBackup.restore(
                                input,
                                passphrase.toCharArray(),
                                StadeBackup.targetsFor(vault),
                                scratch
                            )
                        }
                    }.getOrNull()
                } finally {
                    runCatching { scratch.delete() }
                }
            }
            callback(outcome.toBackupOutcome())
        }
    }

    val verifyDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val passphrase = pending[0]
        pending[0] = null
        if (uri == null || passphrase == null) {
            callback(BackupOutcome.Cancelled)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        StadeBackup.verify(input, passphrase.toCharArray())
                    }
                }.getOrNull()
            }
            callback(outcome.toBackupOutcome())
        }
    }

    return remember(createDocument, openDocument, verifyDocument) {
        BackupIoLauncher(
            onExport = { passphrase ->
                pending[0] = passphrase
                createDocument.launch(defaultBackupFileName())
            },
            onImport = { passphrase ->
                pending[0] = passphrase
                openDocument.launch(arrayOf("*/*"))
            },
            onVerify = { passphrase ->
                pending[0] = passphrase
                verifyDocument.launch(arrayOf("*/*"))
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
