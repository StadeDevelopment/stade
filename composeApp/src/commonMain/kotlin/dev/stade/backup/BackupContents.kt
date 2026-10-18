package dev.stade.backup

import dev.stade.security.Vault

data class BackupContents(
    val payloadBytes: Long,
    val onionKeyIncluded: Boolean
)

expect fun backupContents(vault: Vault): BackupContents
