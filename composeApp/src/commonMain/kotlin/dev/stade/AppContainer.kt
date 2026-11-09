package dev.stade

import app.cash.sqldelight.db.SqlDriver
import dev.stade.chat.ArchiveService
import dev.stade.chat.ArchivedChats
import dev.stade.chat.PinnedChats
import dev.stade.chat.ChatWallpapers
import dev.stade.chat.PinnedMessages
import dev.stade.chat.StarredMessages
import dev.stade.contact.ContactManager
import dev.stade.contact.HandshakeService
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.PqCrypto
import dev.stade.crypto.RatchetSessions
import dev.stade.crypto.platformCrypto
import dev.stade.crypto.platformPq
import dev.stade.db.DatabaseSchema
import dev.stade.db.DatabaseSchemaException
import dev.stade.db.DriverFactory
import dev.stade.db.StadeDb
import dev.stade.group.GroupChatService
import dev.stade.group.GroupManager
import dev.stade.identity.AvatarService
import dev.stade.identity.IdentityManager
import dev.stade.message.ChatService
import dev.stade.message.FingerprintService
import dev.stade.message.MessageManager
import dev.stade.message.ScheduledMessageManager
import dev.stade.message.ScheduledMessageService
import dev.stade.message.TypingTracker
import dev.stade.security.SecretStore
import dev.stade.security.Vault
import dev.stade.stadium.StadiumChatService
import dev.stade.stadium.StadiumManager
import dev.stade.sticker.StickerManager
import dev.stade.sync.Outbox
import dev.stade.sync.SyncEngine
import dev.stade.transport.ConnectionManager
import dev.stade.transport.ConnectionRegistry
import dev.stade.transport.TransportPlugin
import dev.stade.transport.TransportSettings
import dev.stade.vanish.VanishManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AppContainer(
    driverFactory: DriverFactory,
    val vault: Vault,
    transportFactory: (StadeDb) -> List<TransportPlugin> = { emptyList() }
) {
    val crypto: CryptoApi = platformCrypto()
    val pq: PqCrypto = platformPq()

    val db: StadeDb
    private val driver: SqlDriver

    init {
        val databaseKey = vault.databaseKey()
            ?: throw IllegalStateException("the vault must be unlocked before the database is opened")
        val createdDriver = driverFactory.create(vault.databasePath(), databaseKey)
        try {
            DatabaseSchema.requireCompatible(createdDriver)
        } catch (error: DatabaseSchemaException) {
            runCatching { createdDriver.close() }
            throw error
        }
        driver = createdDriver
        val database = StadeDb(createdDriver)
        db = database
    }

    val identities = IdentityManager(db, crypto, pq)
    val contacts = ContactManager(db, crypto)
    val messages = MessageManager(db, crypto)
    val vanish = VanishManager(db, messages)
    val fingerprint = FingerprintService(crypto)
    val handshake = HandshakeService(crypto, pq)
    val outbox = Outbox(db, crypto)
    val ratchet = RatchetSessions(crypto, pq, contacts)
    val groups = GroupManager(db, crypto, pq)
    val stadiums = StadiumManager(db, crypto)
    val stickers = StickerManager(db, crypto)
    val pinnedChats = PinnedChats(db)
    val archivedChats = ArchivedChats(db)
    val starredMessages = StarredMessages(db)
    val pinnedMessages = PinnedMessages(db)
    val chatWallpapers = ChatWallpapers(db)
    val sync = SyncEngine(crypto, pq, contacts, messages, ratchet, outbox, handshake, vanish, groups, stadiums)
    val chat = ChatService(messages, sync, vanish) { dev.stade.message.getTypingSignalEnabled(db) }
    val typing = TypingTracker()
    val scheduledMessages = ScheduledMessageManager(db, crypto)
    val scheduler = ScheduledMessageService(scheduledMessages, chat, contacts)
    val groupChat = GroupChatService(groups, sync, contacts, crypto)
    val archiveService = ArchiveService(archivedChats, sync)
    val avatars = AvatarService(identities, sync, contacts, crypto)
    val stadiumChat = StadiumChatService(stadiums, sync, contacts, crypto, messages, groups)
    val transports = ConnectionRegistry().also { reg ->
        transportFactory(db).forEach { reg.register(it) }
    }
    val transportSettings = TransportSettings(db)
    val connections = ConnectionManager(transports, contacts, sync, transportSettings, isForeground = { isAppInForeground.value }).also { manager ->
        sync.selfAddressesProvider = { manager.selfAddresses() }
        sync.expectedPeerProvider = { address -> manager.expectedPeerFor(address) }
        sync.invitesOpenProvider = { dev.stade.contact.isInviteWindowOpen(db) }
        handshake.onInviteIssued = { dev.stade.contact.openInviteWindow(db) }
    }

    val screenshotSettingTick = MutableStateFlow(0)

    val secrets = SecretStore(db, crypto, vault,
        onScreenshotSettingChanged = { screenshotSettingTick.value++ })

    @Volatile var activeContactId: String? = null

    var isAppInForeground = MutableStateFlow(true)

    val pendingInvite = MutableStateFlow<String?>(null)

    val pendingOpenChat = MutableStateFlow<String?>(null)

    val pendingOpenStadium = MutableStateFlow<String?>(null)

    val pendingOpenGroup = MutableStateFlow<String?>(null)

    val pendingGoHome = MutableStateFlow(false)

    val pendingStickerImport = MutableStateFlow<List<dev.stade.ui.PickedStickerFile>>(emptyList())

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + backgroundFailures)

    @Volatile var isClosed = false
        private set

    suspend fun wipeAllData() {
        isClosed = true
        quiesce()
        pendingInvite.value = null
        pendingOpenChat.value = null
        pendingGoHome.value = false
        pendingStickerImport.value = emptyList()
        activeContactId = null
        runCatching {
            db.stadeDbQueries.transaction {
                db.stadeDbQueries.wipeGroupMessages()
                db.stadeDbQueries.wipeGroupMembers()
                db.stadeDbQueries.wipeGroupDeliveries()
                db.stadeDbQueries.wipeGroupRemovals()
                db.stadeDbQueries.wipeGroups()
                db.stadeDbQueries.wipeStadiumMessages()
                db.stadeDbQueries.wipeStadiumMembers()
                db.stadeDbQueries.wipeStadiums()
                db.stadeDbQueries.wipeOutbox()
                db.stadeDbQueries.wipeMessages()
                db.stadeDbQueries.wipePending()
                db.stadeDbQueries.wipeContacts()
                db.stadeDbQueries.wipeIdentities()
                db.stadeDbQueries.wipeTransports()
                db.stadeDbQueries.wipeKeyValue()
                db.stadeDbQueries.wipeProcessedEnvelope()
                db.stadeDbQueries.wipeStickers()
                db.stadeDbQueries.wipeStickerPacks()
                db.stadeDbQueries.wipeReactions()
                db.stadeDbQueries.wipeVanishSessions()
                db.stadeDbQueries.wipeScheduledMessages()
            }
        }
        runCatching { driver.close() }
        runCatching { dev.stade.media.clearMediaCaches() }
        runCatching { dev.stade.security.clearBiometricUnlock() }
        runCatching { vault.wipe() }
    }

    suspend fun close() {
        if (isClosed) return
        isClosed = true
        quiesce()
        runCatching { driver.close() }
    }

    private suspend fun quiesce() {
        runCatching { connections.stop() }
        val job = appScope.coroutineContext[Job]
        job?.cancel()
        runCatching { withTimeoutOrNull(SHUTDOWN_GRACE_MS) { job?.join() } }
    }
}
