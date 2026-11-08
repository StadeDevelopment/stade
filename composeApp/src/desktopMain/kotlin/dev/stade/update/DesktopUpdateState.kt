package dev.stade.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.stade.APP_VERSION
import dev.stade.AppContainer
import java.io.File

enum class UpdateStage { Idle, Checking, UpToDate, Available, Downloading, Ready, Failed }

object DesktopUpdateState {
    var stage by mutableStateOf(UpdateStage.Idle)
        private set
    var available by mutableStateOf<AvailableUpdate?>(null)
        private set
    var progress by mutableStateOf(0f)
        private set
    var installer by mutableStateOf<File?>(null)
        private set
    var installerSha256: String? = null
        private set

    val hasUpdate: Boolean
        get() = available != null && stage != UpdateStage.Idle && stage != UpdateStage.UpToDate

    suspend fun check(container: AppContainer, silent: Boolean) {
        if (stage == UpdateStage.Checking || stage == UpdateStage.Downloading) return
        if (!silent) stage = UpdateStage.Checking
        val found = runCatching { checkForUpdate(container, APP_VERSION) }.getOrNull()
        when {
            found != null -> {
                available = found
                stage = UpdateStage.Available
            }
            silent -> Unit
            else -> stage = UpdateStage.UpToDate
        }
    }

    suspend fun download(container: AppContainer) {
        val target = available ?: return
        if (stage == UpdateStage.Downloading) return
        stage = UpdateStage.Downloading
        progress = 0f
        when (val result = downloadUpdate(container, target) { progress = it }) {
            is UpdateDownload.Ready -> {
                installer = result.installer
                installerSha256 = result.sha256
                stage = UpdateStage.Ready
            }
            else -> stage = UpdateStage.Failed
        }
    }

    fun dismissToAvailable() {
        stage = if (available != null) UpdateStage.Available else UpdateStage.Idle
    }

    fun markFailed() {
        stage = UpdateStage.Failed
    }
}
