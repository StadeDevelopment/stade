package dev.stade.backup

import dev.stade.security.Vault
import java.io.File

actual fun backupContents(vault: Vault): BackupContents {
    val targets = StadeBackup.targetsFor(vault)
    val database = listOf(targets.plaintextDbPath, targets.encryptedDbPath)
        .map { File(it) }
        .filter { it.isFile }
        .maxOfOrNull { it.length() } ?: 0L
    val meta = File(targets.vaultMetaPath).takeIf { it.isFile }?.length() ?: 0L
    val onion = File(targets.onionKeyPath).takeIf { it.isFile }
    return BackupContents(
        payloadBytes = database + meta + (onion?.length() ?: 0L),
        onionKeyIncluded = onion != null
    )
}
