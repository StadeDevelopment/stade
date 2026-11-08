package dev.stade.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.stade.APP_VERSION
import dev.stade.AppContainer
import dev.stade.ui.i18n.LocalStrings
import dev.stade.update.DesktopUpdateState
import dev.stade.update.UpdateStage
import dev.stade.update.launchInstaller
import dev.stade.update.openReleasePage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

private const val CHECK_DELAY_MS = 8_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun UpdateAvailableButton(container: AppContainer, modifier: Modifier) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(CHECK_DELAY_MS)
        DesktopUpdateState.check(container, silent = true)
    }

    val stage = DesktopUpdateState.stage
    val pending = DesktopUpdateState.available
    if (pending == null) return

    val tooltipText = when (stage) {
        UpdateStage.Downloading -> strings.updateDownloadingTooltip
        else -> strings.updateAvailableTooltip
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltipText) } },
        state = rememberTooltipState(),
        modifier = modifier
    ) {
        IconButton(
            onClick = {
                if (stage == UpdateStage.Available || stage == UpdateStage.Failed) {
                    scope.launch { DesktopUpdateState.download(container) }
                }
            }
        ) {
            if (stage == UpdateStage.Downloading) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { DesktopUpdateState.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = strings.updateAvailableTooltip,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (stage == UpdateStage.Ready) {
        AlertDialog(
            onDismissRequest = { DesktopUpdateState.dismissToAvailable() },
            title = { Text(strings.updateReadyTitle) },
            text = { Text(strings.updateReadyBody, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    val file = DesktopUpdateState.installer
                    val digest = DesktopUpdateState.installerSha256
                    if (file != null && digest != null && launchInstaller(file, digest)) {
                        exitProcess(0)
                    } else {
                        DesktopUpdateState.markFailed()
                    }
                }) { Text(strings.updateInstallAction) }
            },
            dismissButton = {
                TextButton(onClick = { DesktopUpdateState.dismissToAvailable() }) {
                    Text(strings.updateLaterAction)
                }
            }
        )
    }

    if (stage == UpdateStage.Failed) {
        AlertDialog(
            onDismissRequest = { DesktopUpdateState.dismissToAvailable() },
            title = { Text(strings.updateFailedTitle) },
            text = { Text(strings.updateFailedBody, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    openReleasePage(pending.releaseUrl)
                    DesktopUpdateState.dismissToAvailable()
                }) { Text(strings.updateOpenPageAction) }
            },
            dismissButton = {
                TextButton(onClick = { DesktopUpdateState.dismissToAvailable() }) {
                    Text(strings.closeAction)
                }
            }
        )
    }
}
