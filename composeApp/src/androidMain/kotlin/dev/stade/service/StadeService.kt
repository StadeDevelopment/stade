package dev.stade.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.stade.security.getLockOnShutdownEnabled
import android.net.ConnectivityManager
import android.net.Network
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.stade.AppContainer
import dev.stade.MainActivity
import dev.stade.StadeApplication
import dev.stade.notification.NotificationAvatar
import dev.stade.notification.NotificationIds
import dev.stade.notification.getConversationShortcutsEnabled
import dev.stade.notification.getNotificationPrivacyEnabled
import dev.stade.notification.getNotificationsEnabled
import dev.stade.sync.SyncEngine
import dev.stade.backgroundFailures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class StadeService : Service() {
    private val channelId = "stade.connectivity"
    private val msgChannelId = "stade.messages"
    private val notificationId = NotificationIds.FOREGROUND
    private val hiddenNotifId = NotificationIds.HIDDEN_MESSAGES
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + backgroundFailures)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var shutdownReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        dev.stade.ui.i18n.I18n.current =
            dev.stade.ui.i18n.localeToStrings(dev.stade.ui.i18n.getLocalePreference().value)
        ensureChannels()
        startForeground(notificationId, buildForegroundNotification())
        observeMessages()
        registerNetworkCallback()
        registerShutdownReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        networkCallback?.let { cb ->
            runCatching {
                (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(cb)
            }
        }
        networkCallback = null
        shutdownReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        shutdownReceiver = null
        scope.cancel()
        super.onDestroy()
    }

    private fun registerShutdownReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SHUTDOWN)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction("android.intent.action.QUICKBOOT_POWEROFF")
            addAction("com.htc.intent.action.QUICKBOOT_POWEROFF")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (!getLockOnShutdownEnabled().value) return
                val app = application as? StadeApplication ?: return
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    scope.launch {
                        runCatching { app.boot.activeContainer()?.close() }
                        runCatching { app.boot.markLocked() }
                        runCatching { withContext(Dispatchers.IO) { app.vault.flushAndClose() } }
                    }
                } else {
                    runCatching { app.boot.markLocked() }
                    runCatching { app.vault.flushAndClose() }
                }
            }
        }
        runCatching {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onSuccess { shutdownReceiver = receiver }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { notifyNetworkChanged() }
            override fun onLost(network: Network) { notifyNetworkChanged() }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
            .onSuccess { networkCallback = cb }
    }

    private fun notifyNetworkChanged() {
        val app = application as StadeApplication
        app.containerFlow.value?.connections?.onNetworkChanged()
    }


    private fun ensureChannels() {
        val strings = dev.stade.ui.i18n.I18n.current
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(channelId) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(channelId, strings.notifConnectionChannelName, NotificationManager.IMPORTANCE_MIN).apply {
                    description = strings.notifConnectionChannelDesc
                    setShowBadge(false)
                }
            )
        }
        if (mgr.getNotificationChannel(msgChannelId) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(msgChannelId, strings.notifMessagesChannelName, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = strings.notifMessagesChannelDesc
                    setShowBadge(true)
                }
            )
        }
    }


    private fun buildForegroundNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            notificationId,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_GO_HOME, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(dev.stade.ui.i18n.I18n.current.notifRunningTitle)
            .setContentText(dev.stade.ui.i18n.I18n.current.notifRunningText)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }



    private fun pruneStaleShortcuts(container: AppContainer) {
        runCatching {
            val existing = ShortcutManagerCompat.getDynamicShortcuts(this)
            if (existing.isEmpty()) return
            val stale = existing.mapNotNull { shortcut ->
                val id = shortcut.id
                val stillExists = when {
                    id.startsWith("contact_") -> container.contacts.get(id.removePrefix("contact_")) != null
                    id.startsWith("group_") -> container.groups.getGroup(id.removePrefix("group_")) != null
                    id.startsWith("stadium_") -> container.stadiums.getStadium(id.removePrefix("stadium_")) != null
                    else -> true
                }
                if (!stillExists) id else null
            }
            if (stale.isNotEmpty()) {
                ShortcutManagerCompat.removeLongLivedShortcuts(this, stale)
            }
        }
    }

    private fun observeMessages() {
        val app = (application as StadeApplication)
        scope.launch {
            app.containerFlow.collectLatest { container ->
                if (container == null) return@collectLatest
                pruneStaleShortcuts(container)
                launch {
                    container.sync.events.collect { event ->
                        when (event) {
                            is SyncEngine.SyncEvent.MessageReceived -> {
                                if (!getNotificationsEnabled().value) return@collect
                                val contact = container.contacts.get(event.contactId)
                                if (contact?.muted == true) return@collect
                                if (container.isAppInForeground.value && container.activeContactId == event.contactId) return@collect
                                if (getNotificationPrivacyEnabled().value) {
                                    val total = runCatching { container.messages.totalUnread() }.getOrDefault(0L).toInt()
                                    if (total > 0) showPrivacyNotification(total)
                                } else {
                                    val senderName = contact?.nickname ?: dev.stade.ui.i18n.I18n.current.unknownNickname
                                    val preview = container.messages.lastMessage(event.contactId)?.body
                                        ?.let { padAwarePreview(it) }
                                        ?: dev.stade.ui.i18n.I18n.current.notifNewMessageFallback
                                    showMessageNotification(event.contactId, senderName, preview, avatarBytes = contact?.avatar, keySeed = contact?.publicSigningKey)
                                }
                            }
                            is SyncEngine.SyncEvent.GroupMessageReceived -> {
                                if (!getNotificationsEnabled().value) return@collect
                                val group = container.groups.getGroup(event.groupId) ?: return@collect
                                if (group.muted) return@collect
                                if (container.isAppInForeground.value && container.activeContactId == event.groupId) return@collect
                                if (getNotificationPrivacyEnabled().value) {
                                    val total = runCatching { container.messages.totalUnread() }.getOrDefault(0L).toInt()
                                    if (total > 0) showPrivacyNotification(total)
                                    return@collect
                                }
                                val preview = container.groups.lastMessage(event.groupId)?.body
                                    ?.let { padAwarePreview(it) }
                                    ?: dev.stade.ui.i18n.I18n.current.notifNewMessageFallback
                                showMessageNotification(event.groupId, group.name, preview, isGroup = true)
                            }
                            is SyncEngine.SyncEvent.StadiumMessageReceived -> {
                                if (!getNotificationsEnabled().value) return@collect
                                val stadium = container.stadiums.getStadium(event.stadiumId)
                                if (stadium == null || stadium.muted) return@collect
                                if (container.isAppInForeground.value && container.activeContactId == event.stadiumId) return@collect
                                if (getNotificationPrivacyEnabled().value) {
                                    val total = runCatching { container.messages.totalUnread() }.getOrDefault(0L).toInt()
                                    if (total > 0) showPrivacyNotification(total)
                                    return@collect
                                }
                                val preview = container.stadiums.lastMessage(event.stadiumId)?.body
                                    ?.let { padAwarePreview(it) }
                                    ?: dev.stade.ui.i18n.I18n.current.notifNewMessageFallback
                                showMessageNotification(event.stadiumId, stadium.name, preview, isStadium = true)
                            }
                            else -> Unit
                        }
                    }
                }
                launch {
                    val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    container.messages.observeTotalUnread().distinctUntilChanged().collect { count ->
                        if (count == 0L) {
                            mgr.cancel(hiddenNotifId)
                        } else if (getNotificationPrivacyEnabled().value && getNotificationsEnabled().value) {
                            showPrivacyNotification(count.toInt())
                        }
                    }
                }
            }
        }
    }


    private fun showPrivacyNotification(count: Int) {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val openIntent = PendingIntent.getActivity(
            this, hiddenNotifId,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, msgChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Stade")
            .setContentText(dev.stade.ui.i18n.I18n.current.notifNewMessages(count))
            .setNumber(count)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        mgr.notify(hiddenNotifId, notif)
    }



    private fun showMessageNotification(
        contactId: String,
        senderName: String,
        preview: String,
        isStadium: Boolean = false,
        isGroup: Boolean = false,
        avatarBytes: ByteArray? = null,
        keySeed: ByteArray? = null
    ) {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val extraKey = when {
            isStadium -> MainActivity.EXTRA_OPEN_STADIUM_ID
            isGroup -> MainActivity.EXTRA_OPEN_GROUP_ID
            else -> MainActivity.EXTRA_OPEN_CHAT_ID
        }
        val openIntent = PendingIntent.getActivity(
            this,
            contactId.hashCode(),
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(extraKey, contactId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val avatarBitmap = if (isStadium) {
            runCatching { NotificationAvatar.stadiumBitmap(senderName) }.getOrNull()
        } else {
            avatarBytes?.let { NotificationAvatar.fromPhotoBytes(it) }
                ?: runCatching { NotificationAvatar.bitmapFor(senderName, keySeed) }.getOrNull()
        }
        val avatarIcon = avatarBitmap?.let { IconCompat.createWithBitmap(it) }
        val senderPerson = Person.Builder()
            .setName(senderName)
            .setKey(contactId)
            .apply { avatarIcon?.let { setIcon(it) } }
            .build()
        val userPerson = Person.Builder().setName("Me").build()

        val shortcutId = "${if (isStadium) "stadium" else if (isGroup) "group" else "contact"}_$contactId"
        if (getConversationShortcutsEnabled().value) {
            runCatching {
                val shortcutIntent = Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(extraKey, contactId)
                }
                val shortcut = ShortcutInfoCompat.Builder(this, shortcutId)
                    .setShortLabel(senderName)
                    .setLongLived(true)
                    .setIntent(shortcutIntent)
                    .setPerson(senderPerson)
                    .setCategories(setOf("android.shortcut.conversation"))
                    .apply { avatarIcon?.let { setIcon(it) } }
                    .build()
                ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
            }
        }

        val notif = NotificationCompat.Builder(this, msgChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .apply { avatarBitmap?.let { setLargeIcon(it) } }
            .setStyle(
                NotificationCompat.MessagingStyle(userPerson)
                    .addMessage(preview, System.currentTimeMillis(), senderPerson)
            )
            .setContentTitle(senderName)
            .setContentText(preview)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(shortcutId)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        val notifId = (contactId.hashCode() and 0x7FFFFFFF) + 1000
        mgr.notify(notifId, notif)
    }
}

private fun padAwarePreview(body: String): String {
    val strings = dev.stade.ui.i18n.I18n.current
    return when (dev.stade.message.padPreviewKind(body)) {
        dev.stade.message.MessageType.PAD_SOUND -> strings.padSentSound(null, false)
        dev.stade.message.MessageType.UNSUPPORTED -> strings.unsupportedMessage
        else -> dev.stade.message.previewBody(
            body,
            strings.photoMessage,
            strings.voiceMessage,
            strings.videoMessage,
            strings.stickerMessage
        )
    }
}
