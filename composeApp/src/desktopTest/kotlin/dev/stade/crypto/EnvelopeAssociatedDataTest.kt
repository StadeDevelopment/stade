package dev.stade.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val crypto = platformCrypto()
private val pq = platformPq()

private val alice = ByteArray(32) { 1 }
private val bob = ByteArray(32) { 2 }

private fun ad(messageId: String, timestamp: Long, a: ByteArray = alice, b: ByteArray = bob) =
    envelopeAssociatedData(a, b, messageId, timestamp, crypto::hash)

class EnvelopeAssociatedDataTest {

    @Test
    fun bothSidesDeriveTheSameValue() {
        assertContentEquals(
            ad("m1", 10L, alice, bob),
            ad("m1", 10L, bob, alice),
            "the sender and the receiver must agree regardless of key order"
        )
    }

    @Test
    fun theMessageIdIsPartOfTheAuthenticatedData() {
        assertFalse(
            ad("m1", 10L).contentEquals(ad("m2", 10L)),
            "changing the envelope message id must change the authenticated data"
        )
    }

    @Test
    fun theTimestampIsPartOfTheAuthenticatedData() {
        assertFalse(
            ad("m1", 10L).contentEquals(ad("m1", 11L)),
            "changing the envelope timestamp must change the authenticated data"
        )
    }

    @Test
    fun theIdAndTimestampCannotBeTradedOffAgainstEachOther() {
        assertFalse(
            ad("m", 0x3100000000000000L).contentEquals(ad("m1", 0L)),
            "the id and the timestamp must not run together in the authenticated data"
        )
    }

    @Test
    fun aFrameDoesNotOpenWhenTheEnvelopeValuesAreChanged() {
        val ratchet = DoubleRatchet(crypto, pq)
        val aStatic = crypto.generateAgreementKeyPair()
        val bStatic = crypto.generateAgreementKeyPair()
        val root = crypto.randomBytes(32)
        val aKem = pq.generateMlKemKeyPair()
        val bKem = pq.generateMlKemKeyPair()
        val a = ratchet.initSymmetric(root, aStatic, bStatic.publicKey, true, aKem, bKem.publicKey)
        val b = ratchet.initSymmetric(root, bStatic, aStatic.publicKey, false, bKem, aKem.publicKey)

        val frame = ratchet.encrypt(a, "hello".encodeToByteArray(), ad("m1", 10L))

        assertNull(
            ratchet.decrypt(b, frame, ad("m2", 10L)),
            "a receiver must reject a frame whose message id was altered in flight"
        )
        assertNull(
            ratchet.decrypt(b, frame, ad("m1", 11L)),
            "a receiver must reject a frame whose timestamp was altered in flight"
        )
        val opened = ratchet.decrypt(b, frame, ad("m1", 10L))
        assertNotNull(opened, "the untampered frame must still open")
        assertContentEquals("hello".encodeToByteArray(), opened)
    }
}
