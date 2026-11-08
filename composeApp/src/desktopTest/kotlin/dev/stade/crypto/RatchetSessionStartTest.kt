package dev.stade.crypto

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.stade.contact.Contact
import dev.stade.contact.ContactManager
import dev.stade.db.StadeDb
import dev.stade.identity.LocalIdentity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val crypto = platformCrypto()
private val pq = platformPq()

private fun headerOf(frame: ByteArray): DoubleRatchet.Header {
    val len = ((frame[0].toInt() and 0xff) shl 24) or
        ((frame[1].toInt() and 0xff) shl 16) or
        ((frame[2].toInt() and 0xff) shl 8) or
        (frame[3].toInt() and 0xff)
    return DoubleRatchet.Header.decode(frame.copyOfRange(4, 4 + len), 32)!!
}

private fun newIdentity(nickname: String): LocalIdentity {
    val sign = crypto.generateSigningKeyPair()
    val dh = crypto.generateAgreementKeyPair()
    val kem = pq.generateMlKemKeyPair()
    val dsa = pq.generateMlDsaKeyPair()
    return LocalIdentity(
        id = StadeIdFixture.derive(sign.publicKey, dsa.publicKey),
        nickname = nickname,
        publicSigningKey = sign.publicKey,
        privateSigningKey = sign.privateKey,
        publicHandshakeKey = dh.publicKey,
        privateHandshakeKey = dh.privateKey,
        publicMlKemKey = kem.publicKey,
        privateMlKemKey = kem.privateKey,
        publicMlDsaKey = dsa.publicKey,
        privateMlDsaKey = dsa.privateKey,
        createdAt = Clock.System.now().toEpochMilliseconds()
    )
}

private object StadeIdFixture {
    fun derive(signing: ByteArray, mldsa: ByteArray): String =
        dev.stade.identity.StadeId.derive(signing, mldsa, crypto::hash)
}

class RatchetSessionStartTest {

    private fun withSessions(body: suspend (RatchetSessions, LocalIdentity, Contact) -> Unit) = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            StadeDb.Schema.create(driver)
            val db = StadeDb(driver)
            val contacts = ContactManager(db, crypto)
            val owner = newIdentity("me")
            val peer = newIdentity("peer")
            db.stadeDbQueries.insertIdentity(
                owner.id, owner.nickname, owner.publicSigningKey, owner.privateSigningKey,
                owner.publicHandshakeKey, owner.privateHandshakeKey,
                owner.publicMlKemKey, owner.privateMlKemKey,
                owner.publicMlDsaKey, owner.privateMlDsaKey,
                owner.createdAt, null
            )
            val contact = contacts.addFromHandshake(
                owner = owner,
                nickname = "peer",
                peerSigningKey = peer.publicSigningKey,
                peerHandshakeKey = peer.publicHandshakeKey,
                peerMlKemKey = peer.publicMlKemKey,
                peerMlDsaKey = peer.publicMlDsaKey,
                rootKey = crypto.randomBytes(32),
                isAlice = true
            )
            body(RatchetSessions(crypto, pq, contacts), owner, contact)
        } finally {
            driver.close()
        }
    }

    @Test
    fun theFirstMessageOfAFreshSessionCarriesAPostQuantumCiphertext() = withSessions { sessions, owner, contact ->
        val frame = sessions.seal(owner, contact, "first".encodeToByteArray(), "msg-1", 1_000L)

        assertNotNull(
            headerOf(frame).mlkemCt,
            "a brand new session must contribute ML-KEM on its very first ratchet step"
        )
    }

    @Test
    fun aRejectedMessageLeavesTheRatchetWhereItWas() = withSessions { sessions, owner, contact ->
        val accepted = sessions.seal(owner, contact, "one".encodeToByteArray(), "msg-1", 1_000L)
        val counterBefore = headerOf(accepted).counter

        val rejected = sessions.sealIfFits(owner, contact, "two".encodeToByteArray(), "msg-2", 2_000L) { false }
        assertNull(rejected, "a frame that does not fit must not be returned")

        val next = sessions.sealIfFits(owner, contact, "two".encodeToByteArray(), "msg-2", 2_000L) { true }
        assertNotNull(next)
        assertTrue(
            headerOf(next).counter == counterBefore + 1,
            "a rejected send must not consume a ratchet step"
        )
    }
}
