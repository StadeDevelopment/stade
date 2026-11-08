package dev.stade.message

import dev.stade.contact.ContactManager
import dev.stade.identity.LocalIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

const val SCHEDULER_TICK_MS = 15000L

class ScheduledMessageService(
    private val scheduled: ScheduledMessageManager,
    private val chat: ChatService,
    private val contacts: ContactManager
) {
    private var job: Job? = null

    fun start(owner: LocalIdentity, scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                runCatching { dispatchDue(owner) }
                delay(SCHEDULER_TICK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun dispatchDue(owner: LocalIdentity) {
        val now = Clock.System.now().toEpochMilliseconds()
        for (item in scheduled.due(now)) {
            val contact = contacts.get(item.contactId)
            if (contact == null || contact.ownerId != owner.id || contact.kind != 0) {
                scheduled.delete(item.id)
                continue
            }
            val result = runCatching { chat.sendDetailed(owner, contact, item.body, item.replyToId) }.getOrNull()
            if (result == null) continue
            if (result.queued) {
                scheduled.delete(item.id)
            } else {
                runCatching { chat.discardUnsent(result.message.id) }
            }
        }
    }
}
