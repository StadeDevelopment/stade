package dev.stade.ui

import androidx.compose.runtime.Composable
import dev.stade.security.Vault
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

const val MIN_BACKUP_PASSPHRASE_LEN = 8
const val BACKUP_FILE_EXTENSION = "stadebackup"

sealed interface BackupOutcome {
    data object Exported : BackupOutcome
    data class Restored(val onionKeyIncluded: Boolean) : BackupOutcome
    data object Cancelled : BackupOutcome
    data object WrongPassphrase : BackupOutcome
    data object NotABackup : BackupOutcome
    data object Damaged : BackupOutcome
    data object Failed : BackupOutcome
    data object NewerVersion : BackupOutcome
    data class Verified(val onionKeyIncluded: Boolean, val payloadBytes: Long) : BackupOutcome
}

expect class BackupIoLauncher {
    fun exportBackup(passphrase: String)
    fun importBackup(passphrase: String)
    fun verifyBackup(passphrase: String)
}

@Composable
expect fun rememberBackupIo(
    vault: Vault,
    onOutcome: (BackupOutcome) -> Unit
): BackupIoLauncher

fun defaultBackupFileName(): String {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "stade-$today.$BACKUP_FILE_EXTENSION"
}
