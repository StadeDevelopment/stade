package dev.stade

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import dev.stade.notification.clearAllMessageNotifications
import dev.stade.service.StadeService
import dev.stade.ui.StadeApp
import java.io.ByteArrayOutputStream

class MainActivity : FragmentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var lastSecureFlagState: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val app = (application as StadeApplication)
        applySecureScreenFlag(app)
        trackScreenshotSetting(app)
        startForegroundService(Intent(this, StadeService::class.java))
        askNotificationPermissionIfNeeded()
        setContent { StadeApp(app.boot) }
        handleNotificationIntent(intent)
        handleInviteFileIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        handleInviteFileIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val app = application as StadeApplication
        intent?.getStringExtra(EXTRA_OPEN_CHAT_ID)?.let { app.handleOpenChatIntent(it) }
        intent?.getStringExtra(EXTRA_OPEN_STADIUM_ID)?.let { app.handleOpenStadiumIntent(it) }
        intent?.getStringExtra(EXTRA_OPEN_GROUP_ID)?.let { app.handleOpenGroupIntent(it) }
        if (intent?.getBooleanExtra(EXTRA_GO_HOME, false) == true) app.handleGoHomeIntent()
    }

    private fun handleInviteFileIntent(intent: Intent?) {
        intent ?: return
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            handleSharedStickers(extractStreamUris(intent))
            return
        }
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> extractStreamUri(intent)
            else -> null
        }
        uri ?: return
        val app = application as StadeApplication
        lifecycleScope.launch(Dispatchers.IO) {
            val text = try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (out.size() + read > MAX_INVITE_BYTES) {
                            return@use null
                        }
                        out.write(buffer, 0, read)
                    }
                    out.toString(Charsets.UTF_8.name()).trim()
                }
            } catch (_: Exception) {
                null
            }
            if (!text.isNullOrBlank() && text.startsWith("STADE2-")) {
                app.handleOpenInviteIntent(text)
            } else {
                handleSharedStickers(listOf(uri))
            }
        }
    }

    private fun handleSharedStickers(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = application as StadeApplication
        lifecycleScope.launch(Dispatchers.IO) {
            val files = uris.mapNotNull { readSharedSticker(it) }
            if (files.isNotEmpty()) app.handleStickerFilesIntent(files)
        }
    }

    private fun readSharedSticker(uri: Uri): dev.stade.ui.PickedStickerFile? = runCatching {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment ?: "sticker"
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (out.size() + read > dev.stade.sticker.MAX_IMPORT_ARCHIVE_BYTES) return@use null
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: return null
        if (!dev.stade.sticker.looksLikeSticker(bytes) && !dev.stade.sticker.isZipArchive(bytes)) return null
        dev.stade.ui.PickedStickerFile(name, bytes)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun extractStreamUris(intent: Intent): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }.orEmpty()

    @Suppress("DEPRECATION")
    private fun extractStreamUri(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    override fun onStop() {
        super.onStop()
        val app = (application as StadeApplication)
        val vault = app.container?.vault ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { vault.flushAndKeep() }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = (application as StadeApplication)
        applySecureScreenFlag(app)
        clearAllMessageNotifications()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.invalidate()
        }
    }

    private fun screenshotBlockingEnabled(app: StadeApplication): Boolean =
        runCatching {
            app.container?.secrets?.isScreenshotBlockingEnabled()
                ?: app.boot.vault.isScreenshotBlockingEnabled()
        }.getOrDefault(true)

    private fun applySecureScreenFlag(app: StadeApplication) {
        val enabled = screenshotBlockingEnabled(app)
        if (enabled == lastSecureFlagState) return
        lastSecureFlagState = enabled
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun trackScreenshotSetting(app: StadeApplication) {
        lifecycleScope.launch {
            app.containerFlow.collectLatest { container ->
                applySecureScreenFlag(app)
                container?.screenshotSettingTick?.collect { applySecureScreenFlag(app) }
            }
        }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        private const val MAX_INVITE_BYTES = 128 * 1024
        const val EXTRA_OPEN_CHAT_ID = "open_chat_contact_id"
        const val EXTRA_OPEN_STADIUM_ID = "open_stadium_id"
        const val EXTRA_OPEN_GROUP_ID = "open_group_id"
        const val EXTRA_GO_HOME = "go_home"
    }
}
