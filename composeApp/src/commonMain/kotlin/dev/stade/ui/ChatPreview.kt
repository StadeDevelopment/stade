package dev.stade.ui

import dev.stade.AppContainer
import dev.stade.group.GroupMessage
import dev.stade.identity.LocalIdentity
import dev.stade.message.Message
import dev.stade.message.MessageDirection
import dev.stade.message.MessageType
import dev.stade.message.isRetiredBody
import dev.stade.message.previewBody
import dev.stade.message.previewWithSender
import dev.stade.ui.i18n.AppStrings

private fun AppStrings.bodyPreview(body: String): String =
    previewBody(body, photoMessage, voiceMessage, videoMessage, stickerMessage)

fun directChatPreview(message: Message?, strings: AppStrings): String? {
    val msg = message ?: return null
    val isSelf = msg.direction == MessageDirection.OUT
    if (isRetiredBody(msg.body)) return strings.unsupportedMessage
    val sender = if (isSelf) strings.previewYouPrefix else null
    return previewWithSender(sender, strings.bodyPreview(msg.body))
}

fun AppContainer.groupChatPreview(
    groupId: String,
    message: GroupMessage?,
    owner: LocalIdentity,
    strings: AppStrings
): String? {
    val msg = message ?: return null
    val isSelf = msg.isOwn || msg.senderId == owner.stadeId
    val padSender = if (isSelf) null else {
        contacts.get(msg.senderId)?.nickname
            ?: groups.memberIdentity(groupId, msg.senderId)?.nickname?.takeIf { it.isNotBlank() }
            ?: msg.senderId.takeLast(6)
    }
    if (isRetiredBody(msg.body)) return strings.unsupportedMessage
    val sender = if (isSelf) {
        strings.previewYouPrefix
    } else {
        contacts.get(msg.senderId)?.nickname
            ?: groups.memberIdentity(groupId, msg.senderId)?.nickname?.takeIf { it.isNotBlank() }
            ?: msg.senderId.takeLast(6)
    }
    return previewWithSender(sender, strings.bodyPreview(msg.body))
}
