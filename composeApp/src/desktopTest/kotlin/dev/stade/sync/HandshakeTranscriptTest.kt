package dev.stade.sync

import dev.stade.transport.ExpectedPeer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun hello(
    nickname: String = "peer",
    addresses: List<String> = listOf("tor://aaa.onion:5901"),
    reAddRequest: Boolean = false,
    groupProtocol: Int = 2,
    wireProtocol: Int = 2,
    protocolVersion: Int = 5
) = HelloPayload(
    protocolVersion = protocolVersion,
    stadeId = "STADE-AAAA-BBBB-CCCC",
    nickname = nickname,
    signingPublicKey = ByteArray(32) { 1 },
    handshakePublicKey = ByteArray(32) { 2 },
    mlkemPublicKey = ByteArray(1184) { 3 },
    mldsaPublicKey = ByteArray(1952) { 4 },
    nonce = ByteArray(32) { 5 },
    transcriptCommitment = ByteArray(0),
    addresses = addresses,
    reAddRequest = reAddRequest,
    groupProtocol = groupProtocol,
    wireProtocol = wireProtocol,
    ephemeralHandshakeKey = ByteArray(32) { 6 },
    ephemeralMlKemKey = ByteArray(1184) { 7 }
)

class HandshakeTranscriptTest {

    private fun body(h: HelloPayload) = handshakeTranscriptBody(h)

    @Test
    fun theSameHelloAlwaysProducesTheSameTranscript() {
        assertContentEquals(body(hello()), body(hello()))
    }

    @Test
    fun everyControlFieldIsCoveredByTheTranscript() {
        val base = body(hello())
        val variants = mapOf(
            "nickname" to hello(nickname = "someone else"),
            "addresses" to hello(addresses = listOf("tor://bbb.onion:5901")),
            "extra address" to hello(addresses = listOf("tor://aaa.onion:5901", "lan://10.0.0.1:5901")),
            "no address" to hello(addresses = emptyList()),
            "reAddRequest" to hello(reAddRequest = true),
            "groupProtocol" to hello(groupProtocol = 1),
            "wireProtocol" to hello(wireProtocol = 1),
            "protocolVersion" to hello(protocolVersion = 4)
        )
        variants.forEach { (field, variant) ->
            assertFalse(
                base.contentEquals(body(variant)),
                "changing $field must change the signed transcript"
            )
        }
    }

    @Test
    fun addressListsCannotBeRepackedIntoTheSameTranscript() {
        val split = body(hello(addresses = listOf("tor://aaa.onion:5901", "bb")))
        val joined = body(hello(addresses = listOf("tor://aaa.onion:5901bb")))

        assertFalse(
            split.contentEquals(joined),
            "address boundaries must be unambiguous in the transcript"
        )
    }

    @Test
    fun theAuthSignatureCoversBothFlags() {
        val nonce = ByteArray(32) { 9 }
        val a = ByteArray(32) { 1 }
        val b = ByteArray(32) { 2 }
        val base = authSignatureMessage(nonce, a, b, isStadiumJoin = false, noExistingContact = false)

        assertFalse(
            base.contentEquals(authSignatureMessage(nonce, a, b, true, false)),
            "a stadium-join claim must be signed"
        )
        assertFalse(
            base.contentEquals(authSignatureMessage(nonce, a, b, false, true)),
            "a request to drop the existing contact must be signed"
        )
    }

    @Test
    fun theAuthSignatureIsDirectional() {
        val nonce = ByteArray(32) { 9 }
        val a = ByteArray(32) { 1 }
        val b = ByteArray(32) { 2 }

        assertFalse(
            authSignatureMessage(nonce, a, b, false, false)
                .contentEquals(authSignatureMessage(nonce, b, a, false, false)),
            "the two directions must not share a signature message"
        )
    }

    @Test
    fun anExpectedPeerOnlyMatchesItsOwnKeys() {
        val expected = ExpectedPeer("STADE-AAAA-BBBB-CCCC", ByteArray(32) { 1 }, ByteArray(1952) { 4 })

        assertTrue(expected.matches("STADE-AAAA-BBBB-CCCC", ByteArray(32) { 1 }, ByteArray(1952) { 4 }))
        assertFalse(
            expected.matches("STADE-ZZZZ-ZZZZ-ZZZZ", ByteArray(32) { 1 }, ByteArray(1952) { 4 }),
            "a different Stade ID must not satisfy the invite"
        )
        assertFalse(
            expected.matches("STADE-AAAA-BBBB-CCCC", ByteArray(32) { 9 }, ByteArray(1952) { 4 }),
            "a different classical key must not satisfy the invite"
        )
        assertFalse(
            expected.matches("STADE-AAAA-BBBB-CCCC", ByteArray(32) { 1 }, ByteArray(1952) { 9 }),
            "a different post-quantum key must not satisfy the invite"
        )
    }
}
