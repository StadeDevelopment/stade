package dev.stade.message

import dev.stade.audio.MIN_VOICE_DURATION_MS
import dev.stade.contact.Contact
import dev.stade.identity.LocalIdentity
import dev.stade.sync.SyncEngine
import dev.stade.vanish.VanishManager
import dev.stade.vanish.VanishSessionInfo
import kotlinx.datetime.Clock

class ChatService(
    private val messages: MessageManager,
    private val sync: SyncEngine,
    private val vanish: VanishManager
) {
    suspend fun send(owner: LocalIdentity, contact: Contact, body: String, replyToId: String? = null): Message =
        vanish.withLock(contact.id) {
            val session = vanish.currentSessionLocked(contact.id)
            val wireBody = if (replyToId != null) encodeReplyBody(replyToId, body) else body
            val now = Clock.System.now().toEpochMilliseconds()
            val msg = messages.saveOutgoing(contact.id, wireBody, now, session?.sessionId)
            val sentBody = if (session != null) {
                encodeVanishTag(session.sessionId, session.deadlineAtMs, wireBody)
            } else wireBody
            runCatching {
                sync.queueOutgoing(owner, contact, msg.id, sentBody, msg.timestamp)
            }
            msg
        }


    suspend fun sendImage(owner: LocalIdentity, contact: Contact, imageBytes: ByteArray, replyToId: String? = null, caption: String = ""): Message {
        val body = encodeImageBody(imageBytes, caption)
        return send(owner, contact, body, replyToId)
    }

    suspend fun sendVoice(owner: LocalIdentity, contact: Contact, opusBytes: ByteArray, durationMs: Int, replyToId: String? = null): Message {
        require(durationMs >= MIN_VOICE_DURATION_MS && opusBytes.isNotEmpty()) { "voice clip too short" }
        val body = encodeVoiceBody(opusBytes, durationMs)
        return send(owner, contact, body, replyToId)
    }

    suspend fun sendVideo(owner: LocalIdentity, contact: Contact, videoBytes: ByteArray, replyToId: String? = null, caption: String = ""): Message {
        val body = encodeVideoBody(videoBytes, caption)
        return send(owner, contact, body, replyToId)
    }


    suspend fun sendSticker(owner: LocalIdentity, contact: Contact, stickerBytes: ByteArray, replyToId: String? = null): Message {
        val body = encodeStickerBody(stickerBytes)
        return send(owner, contact, body, replyToId)
    }

    suspend fun sendReaction(owner: LocalIdentity, contact: Contact, targetMessageId: String, add: Boolean, emoji: String) {
        val body = encodeReactionBody(targetMessageId, add, emoji)
        val now = Clock.System.now().toEpochMilliseconds()
        runCatching {
            sync.queueOutgoing(owner, contact, messages.newId(), body, now)
        }
    }

    suspend fun sendTyping(owner: LocalIdentity, contact: Contact, typing: Boolean) {
        if (contact.kind != 0) return
        if (!sync.isConnected(contact.id)) return
        val now = Clock.System.now().toEpochMilliseconds()
        runCatching {
            sync.queueOutgoing(owner, contact, messages.newId(), encodeTypingBody(typing), now)
        }
    }

    suspend fun startVanishMode(owner: LocalIdentity, contact: Contact, durationMs: Long): VanishSessionInfo =
        vanish.withLock(contact.id) {
            val now = Clock.System.now().toEpochMilliseconds()
            val session = vanish.startSessionLocked(contact.id, durationMs, now)
            runCatching {
                val body = encodeVanishStartBody(session.sessionId, session.startedAt, session.durationMs)
                sync.queueOutgoing(owner, contact, messages.newId(), body, now)
            }
            session
        }

    suspend fun cancelVanishMode(owner: LocalIdentity, contact: Contact): VanishSessionInfo? =
        vanish.withLock(contact.id) {
            val session = vanish.cancelCurrentSessionLocked(contact.id) ?: return@withLock null
            val now = Clock.System.now().toEpochMilliseconds()
            runCatching {
                sync.queueOutgoing(owner, contact, messages.newId(), encodeVanishCancelBody(session.sessionId), now)
            }
            session
        }
}
