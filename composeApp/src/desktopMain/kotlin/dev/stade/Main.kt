package dev.stade

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import dev.stade.crypto.Encoding
import dev.stade.db.DriverFactory
import dev.stade.db.StadeDb
import dev.stade.notification.DesktopNotifier
import dev.stade.notification.getRunInBackgroundEnabled
import dev.stade.security.Vault
import dev.stade.security.VaultFactory
import dev.stade.transport.LanTransport
import dev.stade.transport.TorTransport
import dev.stade.transport.TransportSettings
import dev.stade.transport.TransportType
import dev.stade.transport.tor.EmbeddedTorManager
import dev.stade.transport.tor.TorBinaryLoader
import dev.stade.ui.StadeApp
import java.security.SecureRandom
import stade.composeapp.generated.resources.Res
import stade.composeapp.generated.resources.app_icon_desktop
import org.jetbrains.compose.resources.painterResource
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

@Volatile
private var desktopContainerRef: AppContainer? = null

fun setDesktopContainer(container: AppContainer?) {
    desktopContainerRef = container
}

fun desktopContainer(): AppContainer? = desktopContainerRef

private val foregroundTick = kotlinx.coroutines.flow.MutableStateFlow(0)

private fun readInviteArg(args: Array<String>): String? {
    val path = args.firstOrNull { it.endsWith(".stadeid", ignoreCase = true) } ?: return null
    return runCatching {
        val text = java.io.File(path).readText().trim()
        if (text.startsWith("STADE2-")) text else null
    }.getOrNull()
}

private fun deliverForwardedInvite(invite: String) {
    val trimmed = invite.trim()
    if (trimmed.startsWith("STADE2-")) {
        val c = desktopContainer()
        if (c != null) c.pendingInvite.value = trimmed else pendingInviteAtBoot = trimmed
    }
    foregroundTick.value = foregroundTick.value + 1
}


private interface Shell32Ext : StdCallLibrary {
    fun SetCurrentProcessExplicitAppUserModelID(appId: WString): Int

    companion object {
        val INSTANCE: Shell32Ext = Native.load("shell32", Shell32Ext::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

private const val WINDOWS_APP_USER_MODEL_ID = "Stade"

private fun applyWindowsAppUserModelId() {
    val os = System.getProperty("os.name", "").lowercase()
    if (!os.contains("win")) return
    runCatching {
        Shell32Ext.INSTANCE.SetCurrentProcessExplicitAppUserModelID(WString(WINDOWS_APP_USER_MODEL_ID))
    }
}

private interface Dwmapi : Library {
    fun DwmSetWindowAttribute(
        hwnd: WinDef.HWND,
        dwAttribute: Int,
        pvAttribute: com.sun.jna.ptr.IntByReference,
        cbAttribute: Int
    ): Int
}

private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

private fun applyDarkTitleBar(window: java.awt.Window) {
    val os = System.getProperty("os.name", "").lowercase()
    if (!os.contains("win")) return
    runCatching {
        val appsUseLightTheme = runCatching {
            com.sun.jna.platform.win32.Advapi32Util.registryGetIntValue(
                com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER,
                "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "AppsUseLightTheme"
            )
        }.getOrDefault(0)
        val hwnd = WinDef.HWND(
            com.sun.jna.Native.getWindowPointer(window)
        )
        val dwmapi = Native.load("dwmapi", Dwmapi::class.java)
        val value = com.sun.jna.ptr.IntByReference(if (appsUseLightTheme == 0) 1 else 0)
        dwmapi.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, value, 4)
    }
}

private fun applyLinuxFontRendering() {
    val os = System.getProperty("os.name", "").lowercase()
    if (!os.contains("linux") && !os.contains("nix")) return
    System.setProperty("awt.useSystemAAFontSettings", "on")
}

private fun applyMacDockIdentity() {
    val os = System.getProperty("os.name", "").lowercase()
    if (!os.contains("mac") && !os.contains("darwin")) return
    System.setProperty("apple.awt.application.name", "Stade")
    runCatching {
        if (!java.awt.Taskbar.isTaskbarSupported()) return@runCatching
        val taskbar = java.awt.Taskbar.getTaskbar()
        if (!taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) return@runCatching
        val cl = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
        val stream = cl.getResourceAsStream("drawable/app_icon_desktop.png")
            ?: cl.getResourceAsStream("composeResources/stade.composeapp.generated.resources/drawable/app_icon_desktop.png")
        val image = stream?.use { javax.imageio.ImageIO.read(it) }
        if (image != null) taskbar.setIconImage(image)
    }
}

fun main(args: Array<String>) {
    applyMacDockIdentity()
    applyWindowsAppUserModelId()
    applyLinuxFontRendering()

    val singleInstanceRoot = java.io.File(System.getProperty("user.home") ?: ".", ".stade")
    val initialInvite = readInviteArg(args)
    val isPrimary = SingleInstance.acquireOrForward(singleInstanceRoot, initialInvite) { invite ->
        deliverForwardedInvite(invite)
    }
    if (!isPrimary) return
    if (initialInvite != null) pendingInviteAtBoot = initialInvite

    application {
        val boot = remember {
            val vault: Vault = VaultFactory().create()
            val torAppRoot = java.io.File(System.getProperty("user.home") ?: ".", ".stade")
            val embeddedTor = EmbeddedTorManager(
                torAppRoot,
                layoutProvider = { TorBinaryLoader.prepare(torAppRoot) }
            )
            val shutdownBoot = java.util.concurrent.atomic.AtomicReference<BootContext?>(null)
            Runtime.getRuntime().addShutdownHook(Thread {
                runCatching { kotlinx.coroutines.runBlocking { embeddedTor.shutdown() } }
                val booted = shutdownBoot.get()
                if (booted != null) {
                    runCatching { booted.lockDownBlocking() }
                } else {
                    runCatching { vault.flushAndClose() }
                }
                runCatching { DesktopNotifier.removeTray() }
            })
            BootContext(
                vault = vault,
                driverFactory = DriverFactory(),
                transportFactory = { db ->
                    val nodeId = deriveNodeId(db)
                    val settings = TransportSettings(db)
                    listOf(
                        LanTransport(nodeId = nodeId),
                        TorTransport(
                            configProvider = { settings.get(TransportType.TOR).config },
                            embedded = embeddedTor
                        )
                    )
                },
                onContainerCreated = { c ->
                    pendingInviteAtBoot?.let { c.pendingInvite.value = it }
                    setDesktopContainer(c)
                }
            ).also { shutdownBoot.set(it) }
        }

        val windowState = rememberWindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            size = DpSize(1100.dp, 740.dp)
        )
        var visible by remember { mutableStateOf(true) }
        val runInBackground by getRunInBackgroundEnabled()

        remember(runInBackground) {
            if (runInBackground && java.awt.SystemTray.isSupported()) {
                DesktopNotifier.setHandlers(
                    onActivate = { visible = true },
                    onQuit = { exitApplication() }
                )
                DesktopNotifier.ensureTray()
            } else {
                DesktopNotifier.removeTray()
            }
        }

        Window(
            onCloseRequest = {
                if (runInBackground && java.awt.SystemTray.isSupported()) {
                    visible = false
                    DesktopNotifier.notifyBackgroundIfFirstTime(
                        "Stade",
                        dev.stade.ui.i18n.I18n.current.backgroundRunningNotice
                    )
                } else {
                    exitApplication()
                }
            },
            state = windowState,
            title = "Stade",
            icon = painterResource(Res.drawable.app_icon_desktop),
            visible = visible,
            undecorated = false
        ) {
            SideEffect {
                window.minimumSize = Dimension(700, 660)
                applyDarkTitleBar(window)
            }

            LaunchedEffect(visible) {
                desktopContainer()?.isAppInForeground?.value = visible
            }
            LaunchedEffect(windowState.isMinimized) {
                if (windowState.isMinimized) {
                    desktopContainer()?.isAppInForeground?.value = false
                } else if (visible) {
                    desktopContainer()?.isAppInForeground?.value = true
                }
            }
            LaunchedEffect(Unit) {
                foregroundTick.collect { tick ->
                    if (tick > 0) {
                        visible = true
                        windowState.isMinimized = false
                        java.awt.EventQueue.invokeLater {
                            runCatching {
                                window.toFront()
                                window.requestFocus()
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    StadeApp(boot)
                }
            }
        }
    }
}

private var pendingInviteAtBoot: String? = null

private fun deriveNodeId(db: StadeDb): String {
    val key = "node.id"
    val existing = db.stadeDbQueries.getKv(key).executeAsOneOrNull()
    if (existing != null) return Encoding.toHex(existing)
    val rnd = ByteArray(16)
    SecureRandom().nextBytes(rnd)
    db.stadeDbQueries.putKv(key, rnd)
    return Encoding.toHex(rnd)
}