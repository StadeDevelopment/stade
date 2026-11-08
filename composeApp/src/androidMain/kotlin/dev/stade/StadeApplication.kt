package dev.stade

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dev.stade.crypto.Encoding
import dev.stade.db.DriverFactory
import dev.stade.db.StadeDb
import dev.stade.security.Vault
import dev.stade.security.VaultFactory
import dev.stade.transport.LanTransport
import dev.stade.transport.TorTransport
import dev.stade.transport.TransportSettings
import dev.stade.transport.TransportType
import dev.stade.transport.tor.AndroidTorBinaryLoader
import dev.stade.transport.tor.EmbeddedTorManager

class StadeApplication : Application() {
    lateinit var boot: BootContext
        private set

    val container: AppContainer?
        get() = activeContainer

    private var activeContainer: AppContainer? = null

    val containerFlow = kotlinx.coroutines.flow.MutableStateFlow<AppContainer?>(null)

    private var pendingChatAtBoot: String? = null
    private var pendingStadiumAtBoot: String? = null
    private var pendingGroupAtBoot: String? = null
    private var pendingGoHomeAtBoot: Boolean = false
    private var pendingInviteAtBoot: String? = null
    private var pendingStickersAtBoot: List<dev.stade.ui.PickedStickerFile> = emptyList()

    fun handleOpenChatIntent(contactId: String) {
        val c = activeContainer
        if (c != null) c.pendingOpenChat.value = contactId else pendingChatAtBoot = contactId
    }

    fun handleOpenInviteIntent(inviteText: String) {
        val c = activeContainer
        if (c != null) c.pendingInvite.value = inviteText else pendingInviteAtBoot = inviteText
    }

    fun handleStickerFilesIntent(files: List<dev.stade.ui.PickedStickerFile>) {
        if (files.isEmpty()) return
        val c = activeContainer
        if (c != null) c.pendingStickerImport.value = files else pendingStickersAtBoot = files
    }

    fun handleOpenStadiumIntent(stadiumId: String) {
        val c = activeContainer
        if (c != null) c.pendingOpenStadium.value = stadiumId else pendingStadiumAtBoot = stadiumId
    }

    fun handleOpenGroupIntent(groupId: String) {
        val c = activeContainer
        if (c != null) c.pendingOpenGroup.value = groupId else pendingGroupAtBoot = groupId
    }

    fun handleGoHomeIntent() {
        val c = activeContainer
        if (c != null) c.pendingGoHome.value = true else pendingGoHomeAtBoot = true
    }

    lateinit var vault: Vault
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        vault = VaultFactory(this).create()
        val driver = DriverFactory(this)
        val torAppRoot = java.io.File(filesDir, "stade")
        val embeddedTor = runCatching {
            EmbeddedTorManager(torAppRoot, layoutProvider = { AndroidTorBinaryLoader.prepare(this, torAppRoot) })
        }.getOrNull()
        boot = BootContext(
            vault = vault,
            driverFactory = driver,
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
                activeContainer = c
                containerFlow.value = c
                pendingChatAtBoot?.let { c.pendingOpenChat.value = it }
                pendingChatAtBoot = null
                pendingStadiumAtBoot?.let { c.pendingOpenStadium.value = it }
                pendingStadiumAtBoot = null
                pendingGroupAtBoot?.let { c.pendingOpenGroup.value = it }
                pendingGroupAtBoot = null
                if (pendingGoHomeAtBoot) {
                    c.pendingGoHome.value = true
                    pendingGoHomeAtBoot = false
                }
                pendingInviteAtBoot?.let { c.pendingInvite.value = it }
                pendingInviteAtBoot = null
                if (pendingStickersAtBoot.isNotEmpty()) {
                    c.pendingStickerImport.value = pendingStickersAtBoot
                    pendingStickersAtBoot = emptyList()
                }
            }
        )
        var startedCount = 0
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedCount++
                activeContainer?.isAppInForeground?.value = true
            }
            override fun onActivityStopped(activity: Activity) {
                if (--startedCount <= 0) {
                    startedCount = 0
                    activeContainer?.isAppInForeground?.value = false
                    boot.noteLeftForeground()
                }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { kotlinx.coroutines.runBlocking { embeddedTor?.shutdown() } }
            runCatching { boot.lockDownBlocking() }
        })
    }

    override fun onTerminate() {
        runCatching { boot.lockDownBlocking() }
        super.onTerminate()
    }

    private fun deriveNodeId(db: StadeDb): String {
        val key = "node.id"
        val existing = db.stadeDbQueries.getKv(key).executeAsOneOrNull()
        if (existing != null) return Encoding.toHex(existing)
        val rnd = ByteArray(16)
        java.security.SecureRandom().nextBytes(rnd)
        db.stadeDbQueries.putKv(key, rnd)
        return Encoding.toHex(rnd)
    }

    companion object {
        lateinit var instance: StadeApplication
            private set
    }
}
