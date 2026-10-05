package dev.stade.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import dev.stade.APP_VERSION
import dev.stade.AppContainer
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.screens.NavigationSettingsRow
import dev.stade.ui.screens.SettingsSectionLabel
import dev.stade.update.DesktopUpdateState
import dev.stade.update.UpdateStage
import kotlinx.coroutines.launch

@Composable
actual fun UpdateSettingsSection(container: AppContainer) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val stage = DesktopUpdateState.stage
    val available = DesktopUpdateState.available

    val subtitle = when {
        stage == UpdateStage.Checking -> strings.updateChecking
        stage == UpdateStage.UpToDate -> strings.updateUpToDate
        available != null -> strings.updateFoundVersion(available.version)
        else -> "Stade $APP_VERSION"
    }

    SettingsSectionLabel(strings.updateSection)
    SettingsGroup {
        row {
            NavigationSettingsRow(
                icon = Icons.Default.SystemUpdateAlt,
                iconTint = MaterialTheme.colorScheme.primary,
                title = strings.updateCheckTitle,
                subtitle = subtitle,
                onClick = {
                    if (stage != UpdateStage.Checking && stage != UpdateStage.Downloading) {
                        scope.launch { DesktopUpdateState.check(container, silent = false) }
                }
                }
            )
        }
    }
}
