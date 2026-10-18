package dev.stade

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import dev.stade.chat.ArchiveService
import dev.stade.chat.ArchivedChats
import dev.stade.chat.PinnedChats
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
        val createdDriver = driverFactory.create(vault.plaintextDbPath())
        try {
            DatabaseSchema.requireCompatible(createdDriver)
        } catch (error: DatabaseSchemaException) {
            runCatching { createdDriver.close() }
            throw error
        }
        driver = createdDriver
        val database = StadeDb(createdDriver)
        runCatching {
            database.stadeDbQueries.putKv("schema.version", "2".encodeToByteArray())
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT id FROM StadeGroup LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS StadeGroup (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, name TEXT NOT NULL, inviteToken TEXT NOT NULL, createdAt INTEGER NOT NULL, creatorStadeId TEXT NOT NULL DEFAULT '')", 0) }
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS GroupMember (groupId TEXT NOT NULL, contactId TEXT NOT NULL, joinedAt INTEGER NOT NULL, PRIMARY KEY(groupId, contactId))", 0) }
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS GroupMessage (id TEXT NOT NULL PRIMARY KEY, groupId TEXT NOT NULL, senderId TEXT NOT NULL, body TEXT NOT NULL, timestamp INTEGER NOT NULL, outgoing INTEGER NOT NULL DEFAULT 0, read INTEGER NOT NULL DEFAULT 0)", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxGroupMessage ON GroupMessage(groupId, timestamp)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT creatorStadeId FROM StadeGroup LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE StadeGroup ADD COLUMN creatorStadeId TEXT NOT NULL DEFAULT ''", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT messageId FROM MessageReaction LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS MessageReaction (messageId TEXT NOT NULL, fromId TEXT NOT NULL, emoji TEXT NOT NULL, PRIMARY KEY(messageId, fromId))", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxMessageReaction ON MessageReaction(messageId)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT kind FROM Contact LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE Contact ADD COLUMN kind INTEGER NOT NULL DEFAULT 0", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT id FROM Stadium LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS Stadium (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, name TEXT NOT NULL, creatorStadeId TEXT NOT NULL, isOwner INTEGER NOT NULL DEFAULT 0, inviteToken TEXT NOT NULL, memberCount INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, muted INTEGER NOT NULL DEFAULT 0)", 0) }
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS StadiumMember (stadiumId TEXT NOT NULL, contactId TEXT NOT NULL, joinedAt INTEGER NOT NULL, PRIMARY KEY(stadiumId, contactId))", 0) }
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS StadiumMessage (id TEXT NOT NULL PRIMARY KEY, stadiumId TEXT NOT NULL, body TEXT NOT NULL, timestamp INTEGER NOT NULL, outgoing INTEGER NOT NULL DEFAULT 0)", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxStadiumMessage ON StadiumMessage(stadiumId, timestamp)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT muted FROM Stadium LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE Stadium ADD COLUMN muted INTEGER NOT NULL DEFAULT 0", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT muted FROM Contact LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE Contact ADD COLUMN muted INTEGER NOT NULL DEFAULT 0", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT bio FROM LocalIdentity LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE LocalIdentity ADD COLUMN bio TEXT NOT NULL DEFAULT ''", 0) }
            runCatching { createdDriver.execute(null, "ALTER TABLE LocalIdentity ADD COLUMN status TEXT NOT NULL DEFAULT ''", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT bio FROM Contact LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE Contact ADD COLUMN bio TEXT NOT NULL DEFAULT ''", 0) }
            runCatching { createdDriver.execute(null, "ALTER TABLE Contact ADD COLUMN status TEXT NOT NULL DEFAULT ''", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT about FROM Stadium LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE Stadium ADD COLUMN about TEXT NOT NULL DEFAULT ''", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT muted FROM StadeGroup LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE StadeGroup ADD COLUMN muted INTEGER NOT NULL DEFAULT 0", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT messageId FROM ProcessedEnvelope LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS ProcessedEnvelope (messageId TEXT NOT NULL PRIMARY KEY, contactId TEXT NOT NULL, processedAt INTEGER NOT NULL)", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxProcessedEnvelope ON ProcessedEnvelope(processedAt)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT id FROM Sticker LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS Sticker (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, imageBytes BLOB NOT NULL, createdAt INTEGER NOT NULL)", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxSticker ON Sticker(ownerId)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT packId, contentHash FROM Sticker LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE Sticker ADD COLUMN packId TEXT", 0) }
            runCatching { createdDriver.execute(null, "ALTER TABLE Sticker ADD COLUMN contentHash TEXT NOT NULL DEFAULT ''", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxStickerPackRef ON Sticker(packId)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT id FROM StickerPack LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS StickerPack (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, title TEXT NOT NULL, author TEXT NOT NULL DEFAULT '', origin TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL)", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxStickerPack ON StickerPack(ownerId)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT sessionId FROM VanishSession LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS VanishSession (sessionId TEXT NOT NULL PRIMARY KEY, contactId TEXT NOT NULL, startedAt INTEGER NOT NULL, durationMs INTEGER NOT NULL)", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxVanishSessionContact ON VanishSession(contactId)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT vanishSessionId FROM Message LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE Message ADD COLUMN vanishSessionId TEXT", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxMessageVanishSession ON Message(vanishSessionId)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT avatar FROM Contact LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE Contact ADD COLUMN avatar BLOB", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT avatar FROM LocalIdentity LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE LocalIdentity ADD COLUMN avatar BLOB", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT messageId FROM GroupDelivery LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS GroupDelivery (messageId TEXT NOT NULL, memberId TEXT NOT NULL, PRIMARY KEY(messageId, memberId))", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT groupProto FROM Contact LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE Contact ADD COLUMN groupProto INTEGER NOT NULL DEFAULT 1", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT signingKey FROM GroupMember LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE GroupMember ADD COLUMN nickname TEXT NOT NULL DEFAULT ''", 0) }
            runCatching { createdDriver.execute(null, "ALTER TABLE GroupMember ADD COLUMN signingKey BLOB", 0) }
            runCatching { createdDriver.execute(null, "ALTER TABLE GroupMember ADD COLUMN mldsaKey BLOB", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT id FROM ScheduledMessage LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS ScheduledMessage (id TEXT NOT NULL PRIMARY KEY, contactId TEXT NOT NULL, body TEXT NOT NULL, replyToId TEXT, scheduledAt INTEGER NOT NULL, createdAt INTEGER NOT NULL)", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxScheduledMessageContact ON ScheduledMessage(contactId, scheduledAt)", 0) }
        }
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
    val sync = SyncEngine(crypto, pq, contacts, messages, ratchet, outbox, handshake, vanish, groups, stadiums)
    val chat = ChatService(messages, sync, vanish)
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
    val connections = ConnectionManager(transports, contacts, sync, transportSettings, isForeground = { isAppInForeground.value }).also {
        sync.selfAddressesProvider = { it.selfAddresses() }
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
