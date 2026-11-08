package dev.stade.crypto

import dev.stade.contact.Contact
import dev.stade.contact.ContactManager
import dev.stade.identity.LocalIdentity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class RatchetSessions(
    private val crypto: CryptoApi,
    private val pq: PqCrypto,
    private val contacts: ContactManager
) {
    private val ratchet = DoubleRatchet(crypto, pq)
    private val states = mutableMapOf<String, DoubleRatchet.State>()
    private val locks = mutableMapOf<String, Mutex>()
    private val locksMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private suspend fun lockFor(contactId: String): Mutex =
        locksMutex.withLock { locks.getOrPut(contactId) { Mutex() } }

    private fun loadOrInit(owner: LocalIdentity, contact: Contact): DoubleRatchet.State {
        states[contact.id]?.let { return it }
        val saved = contact.ratchetState
        val state = if (saved != null) {
            val text = saved.decodeToString()
            val snap = runCatching { json.decodeFromString(RatchetSnapshot.serializer(), text) }
                .getOrElse { json.decodeFromString(LegacyRatchetSnapshot.serializer(), text).toCurrent() }
            val loaded = RatchetSerializer.fromSnapshot(snap)
            if (loaded.sendChainKey == null || loaded.recvChainKey == null || loaded.dhRecvPub == null) {
                ratchet.initSymmetric(
                    rootSeed = contact.rootKey,
                    ownDh = KeyPair(owner.publicHandshakeKey, owner.privateHandshakeKey),
                    peerDhPub = contact.publicHandshakeKey,
                    isAlice = contact.isAlice,
                    ownKem = KeyPair(owner.publicMlKemKey, owner.privateMlKemKey),
                    peerKemPub = contact.publicMlKemKey
                )
            } else {
                if (contact.isAlice && ratchet.hasNeverRatcheted(loaded, owner.publicHandshakeKey)) {
                    ratchet.startSendRatchet(loaded)
                }
                loaded
            }
        } else {
            ratchet.initSymmetric(
                rootSeed = contact.rootKey,
                ownDh = KeyPair(owner.publicHandshakeKey, owner.privateHandshakeKey),
                peerDhPub = contact.publicHandshakeKey,
                isAlice = contact.isAlice,
                ownKem = KeyPair(owner.publicMlKemKey, owner.privateMlKemKey),
                peerKemPub = contact.publicMlKemKey
            )
        }
        states[contact.id] = state
        return state
    }

    private fun persist(contactId: String, state: DoubleRatchet.State) {
        val snap = RatchetSerializer.toSnapshot(state)
        val bytes = json.encodeToString(RatchetSnapshot.serializer(), snap).encodeToByteArray()
        contacts.saveRatchet(contactId, bytes)
    }

    suspend fun seal(owner: LocalIdentity, contact: Contact, plaintext: ByteArray): ByteArray =
        lockFor(contact.id).withLock {
            val fresh = contacts.get(contact.id) ?: contact
            val state = loadOrInit(owner, fresh)
            val ad = symmetricAd(owner.publicSigningKey, fresh.publicSigningKey)
            val out = ratchet.encrypt(state, plaintext, ad)
            persist(contact.id, state)
            out
        }

    suspend fun sealIfFits(
        owner: LocalIdentity,
        contact: Contact,
        plaintext: ByteArray,
        fits: (ByteArray) -> Boolean
    ): ByteArray? =
        lockFor(contact.id).withLock {
            val fresh = contacts.get(contact.id) ?: contact
            val state = loadOrInit(owner, fresh)
            val rollback = RatchetSerializer.toSnapshot(state)
            val ad = symmetricAd(owner.publicSigningKey, fresh.publicSigningKey)
            val out = ratchet.encrypt(state, plaintext, ad)
            if (!fits(out)) {
                states[contact.id] = RatchetSerializer.fromSnapshot(rollback)
                return@withLock null
            }
            persist(contact.id, state)
            out
        }

    suspend fun open(owner: LocalIdentity, contact: Contact, frame: ByteArray): ByteArray? =
        lockFor(contact.id).withLock {
            val fresh = contacts.get(contact.id) ?: contact
            val state = loadOrInit(owner, fresh)
            val ad = symmetricAd(owner.publicSigningKey, fresh.publicSigningKey)
            val out = ratchet.decrypt(state, frame, ad)
            if (out != null) {
                persist(contact.id, state)
            }
            out
        }

    private fun symmetricAd(a: ByteArray, b: ByteArray): ByteArray {
        val cmp = compareLex(a, b)
        val concat = if (cmp <= 0) a + b else b + a
        return crypto.hash(concat)
    }

    private fun compareLex(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a[i].toInt() and 0xff
            val y = b[i].toInt() and 0xff
            if (x != y) return x - y
        }
        return a.size - b.size
    }

    suspend fun forget(contactId: String) {
        locksMutex.withLock {
            states.remove(contactId)
            locks.remove(contactId)
        }
    }
}
