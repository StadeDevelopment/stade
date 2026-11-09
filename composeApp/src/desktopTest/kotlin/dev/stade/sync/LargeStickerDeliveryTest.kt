package dev.stade.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.stade.contact.ContactManager
import dev.stade.contact.HandshakeService
import dev.stade.crypto.RatchetSessions
import dev.stade.crypto.platformCrypto
import dev.stade.crypto.platformPq
import dev.stade.db.StadeDb
import dev.stade.identity.LocalIdentity
import dev.stade.identity.StadeId
import dev.stade.message.MessageManager
import dev.stade.message.encodeStickerBody
import dev.stade.transport.Connection
import dev.stade.vanish.VanishManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val crypto = platformCrypto()
private val pq = platformPq()

private class WirePipe {
    val aToB = Channel<ByteArray>(Channel.UNLIMITED)
    val bToA = Channel<ByteArray>(Channel.UNLIMITED)
}

private class WireEnd(
    private val outbound: Channel<ByteArray>,
    private val inbound: Channel<ByteArray>,
    override val remoteAddress: String
) : Connection {
    @Volatile var closed = false
    @Volatile var largestSent = 0
    override suspend fun send(frame: ByteArray) {
        if (closed) throw IllegalStateException("closed")
        if (frame.size > largestSent) largestSent = frame.size
        outbound.send(frame)
    }
    override suspend fun receive(): ByteArray? = if (closed) null else inbound.receive()
    override suspend fun close() { closed = true }
}

private class Node(val name: String) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    val db: StadeDb
    val contacts: ContactManager
    val messages: MessageManager
    val engine: SyncEngine
    val identity: LocalIdentity

    init {
        StadeDb.Schema.create(driver)
        db = StadeDb(driver)
        contacts = ContactManager(db, crypto)
        messages = MessageManager(db, crypto)
        val vanish = VanishManager(db, messages)
        val ratchet = RatchetSessions(crypto, pq, contacts)
        val outbox = Outbox(db, crypto)
        engine = SyncEngine(crypto, pq, contacts, messages, ratchet, outbox, HandshakeService(crypto, pq), vanish)
        val sign = crypto.generateSigningKeyPair()
        val dh = crypto.generateAgreementKeyPair()
        val kem = pq.generateMlKemKeyPair()
        val dsa = pq.generateMlDsaKeyPair()
        identity = LocalIdentity(
            id = StadeId.derive(sign.publicKey, dsa.publicKey, crypto::hash),
            nickname = name,
            publicSigningKey = sign.publicKey, privateSigningKey = sign.privateKey,
            publicHandshakeKey = dh.publicKey, privateHandshakeKey = dh.privateKey,
            publicMlKemKey = kem.publicKey, privateMlKemKey = kem.privateKey,
            publicMlDsaKey = dsa.publicKey, privateMlDsaKey = dsa.privateKey,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
        db.stadeDbQueries.insertIdentity(
            identity.id, identity.nickname, identity.publicSigningKey, identity.privateSigningKey,
            identity.publicHandshakeKey, identity.privateHandshakeKey,
            identity.publicMlKemKey, identity.privateMlKemKey,
            identity.publicMlDsaKey, identity.privateMlDsaKey, identity.createdAt, null
        )
    }

    fun close() = driver.close()
}

class LargeStickerDeliveryTest {

    private val nodes = mutableListOf<Node>()

    @AfterTest
    fun tearDown() {
        nodes.forEach { runCatching { it.close() } }
    }

    private fun gif(size: Int): ByteArray {
        val out = ByteArray(size)
        "GIF89a".encodeToByteArray().copyInto(out, 0)
        for (i in 6 until size) out[i] = ((i * 31) and 0xff).toByte()
        return out
    }

    private suspend fun deliver(scope: CoroutineScope, gifSize: Int): Triple<Boolean, Boolean, Int> {
        val a = Node("alice").also { nodes += it }
        val b = Node("bob").also { nodes += it }
        val pipe = WirePipe()
        val endA = WireEnd(pipe.aToB, pipe.bToA, "tor://bob.onion:5901")
        val endB = WireEnd(pipe.bToA, pipe.aToB, "tor://alice.onion:5901")

        val jobs = mutableListOf<Job>()
        jobs += scope.launch { a.engine.handleConnection(a.identity, endA, outbound = true) }
        jobs += scope.launch { b.engine.handleConnection(b.identity, endB, outbound = false) }

        val linked = withTimeoutOrNull(20_000) {
            while (a.engine.connectedContacts.value.isEmpty() ||
                b.engine.connectedContacts.value.isEmpty()
            ) delay(20)
            true
        } ?: false
        if (!linked) {
            jobs.forEach { it.cancel() }
            return Triple(false, false, 0)
        }

        val contactB = a.contacts.findByStadeId(b.identity.id)
        assertNotNull(contactB, "alice must know bob after the handshake")

        val body = encodeStickerBody(gif(gifSize))
        val queued = a.engine.queueOutgoing(
            a.identity, contactB, a.messages.newId(), body,
            Clock.System.now().toEpochMilliseconds()
        )

        val arrived = withTimeoutOrNull(30_000) {
            while (b.db.stadeDbQueries.selectMessages(a.identity.id).executeAsList().isEmpty()) delay(25)
            true
        } ?: false

        val stillUp = a.engine.connectedContacts.value.isNotEmpty() &&
            b.engine.connectedContacts.value.isNotEmpty()
        jobs.forEach { it.cancel() }
        return Triple(queued && arrived, stillUp, endA.largestSent)
    }

    @Test
    fun aSmallStickerIsDeliveredAndTheSessionSurvives() = runTest {
        val (delivered, stillUp, largest) = deliver(this, 40_000)
        println("small sticker: delivered=$delivered sessionAlive=$stillUp largestFrame=$largest")
        assertTrue(delivered, "a small sticker must arrive")
        assertTrue(stillUp, "the session must survive a small sticker")
    }

    @Test
    fun aFullSizeStickerIsDeliveredAndTheSessionSurvives() = runTest {
        val (delivered, stillUp, largest) = deliver(this, dev.stade.message.MAX_ATTACHMENT_BYTES)
        println("full sticker: delivered=$delivered sessionAlive=$stillUp largestFrame=$largest")
        assertTrue(stillUp, "sending a full-size sticker must not drop the connection")
        assertTrue(delivered, "a full-size sticker must arrive")
    }
}
