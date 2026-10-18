package dev.stade.notification

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import dev.stade.ui.i18n.I18n
import java.util.prefs.Preferences

private val prefs: Preferences = Preferences.userRoot().node("dev/stade/notifications")

private const val KEY_ENABLED = "enabled"
private const val KEY_PRIVACY = "privacy"
private const val KEY_BACKGROUND = "background"

actual val isNotificationSupported: Boolean = java.awt.SystemTray.isSupported()

actual val isSystemNotificationSettingsSupported: Boolean = false

private val _enabled = mutableStateOf(prefs.getBoolean(KEY_ENABLED, true))
private val _privacy = mutableStateOf(prefs.getBoolean(KEY_PRIVACY, false))
private val _runInBackground = mutableStateOf(prefs.getBoolean(KEY_BACKGROUND, true))

actual fun getNotificationsEnabled(): State<Boolean> = _enabled
actual fun setNotificationsEnabled(value: Boolean) {
    _enabled.value = value
    prefs.putBoolean(KEY_ENABLED, value)
    if (!value) DesktopNotifier.cancelAll()
}

actual fun getNotificationPrivacyEnabled(): State<Boolean> = _privacy
actual fun setNotificationPrivacyEnabled(value: Boolean) {
    _privacy.value = value
    prefs.putBoolean(KEY_PRIVACY, value)
}

actual fun openNotificationSettings() {
}

actual fun cancelMessagesNotification(contactId: String) {
    DesktopNotifier.cancel(contactId)
}

actual fun clearAllMessageNotifications() {
    DesktopNotifier.cancelAll()
}

actual fun showIncomingMessageNotification(
    contactId: String,
    senderName: String,
    preview: String,
    privacy: Boolean,
    unreadTotal: Int
) {
    if (!_enabled.value) return
    if (privacy) {
        DesktopNotifier.showMessage("Stade", I18n.current.notifNewMessages(unreadTotal))
    } else {
        DesktopNotifier.showMessage(senderName, preview)
    }
}

fun getRunInBackgroundEnabled(): State<Boolean> = _runInBackground
fun setRunInBackgroundEnabled(value: Boolean) {
    _runInBackground.value = value
    prefs.putBoolean(KEY_BACKGROUND, value)
}

actual val isRunInBackgroundSupported: Boolean = java.awt.SystemTray.isSupported()
actual fun getRunInBackgroundEnabledCommon(): State<Boolean> = _runInBackground
actual fun setRunInBackgroundEnabledCommon(value: Boolean) {
    setRunInBackgroundEnabled(value)
}

actual val isConversationShortcutsSupported: Boolean = false
private val _conversationShortcutsEnabled = mutableStateOf(true)
actual fun getConversationShortcutsEnabled(): State<Boolean> = _conversationShortcutsEnabled
actual fun setConversationShortcutsEnabled(value: Boolean) {}
actual fun removeConversationShortcut(kind: ShortcutEntityKind, id: String) {}
