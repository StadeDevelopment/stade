package dev.stade.contact

import dev.stade.crypto.CryptoApi
import dev.stade.crypto.Encoding
import dev.stade.crypto.KemResult
import dev.stade.crypto.KeyPair
import dev.stade.crypto.PqCrypto
import dev.stade.identity.LocalIdentity
import dev.stade.identity.StadeId

data class HandshakeEphemeral(val dh: KeyPair, val kem: KeyPair)

data class PeerEphemeral(val dhPub: ByteArray, val kemPub: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is PeerEphemeral && other.dhPub.contentEquals(dhPub) && other.kemPub.contentEquals(kemPub)
    override fun hashCode(): Int = dhPub.contentHashCode() * 31 + kemPub.contentHashCode()
}

data class InvitePayload(
    val stadeId: String,
    val nickname: String,
    val signingPublicKey: ByteArray,
    val handshakePublicKey: ByteArray,
    val mlkemPublicKey: ByteArray,
    val mldsaPublicKey: ByteArray,
    val addresses: List<String> = emptyList()
)

data class InviteCode(
    val display: String,
    val raw: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        other is InviteCode && display == other.display && raw.contentEquals(other.raw)
    override fun hashCode(): Int = display.hashCode() * 31 + raw.contentHashCode()
}

sealed class InviteParseResult {
    data class Ok(val payload: InvitePayload) : InviteParseResult()
    data class MissingPrefix(val firstChars: String) : InviteParseResult()
    data class TooShort(val expected: Int, val actual: Int) : InviteParseResult()
    data object BadMagic : InviteParseResult()
    data class BadVersion(val version: Int) : InviteParseResult()
    data class BadNickname(val length: Int) : InviteParseResult()
    data class BadAddressBlob(val length: Int) : InviteParseResult()
    data object EdVerifyFail : InviteParseResult()
    data object MlDsaVerifyFail : InviteParseResult()
    data class TrailingBytes(val extra: Int) : InviteParseResult()
    data class DecodeError(val cause: String) : InviteParseResult()
}

class HandshakeService(
    private val crypto: CryptoApi,
    private val pq: PqCrypto
) {

    private val magic = byteArrayOf(0x53, 0x54, 0x44, 0x32)
    private val version: Byte = 0x02


    fun createInvite(owner: LocalIdentity, addresses: List<String> = emptyList()): InviteCode {
        val cleanAddrs = addresses.filter { it.isNotBlank() }.distinct()
        val tbs = composeTbs(
            nickname = owner.nickname,
            edPub = owner.publicSigningKey,
            xPub = owner.publicHandshakeKey,
            kemPub = owner.publicMlKemKey,
            dsaPub = owner.publicMlDsaKey,
            addresses = cleanAddrs
        )
        val edSig = crypto.sign(owner.privateSigningKey, tbs)
        val dsaSig = pq.signMlDsa(owner.privateMlDsaKey, owner.publicMlDsaKey, tbs)
        val raw = tbs + edSig + dsaSig
        val b32 = Encoding.toBase32(raw)
        return InviteCode(display = "STADE2-$b32", raw = raw)
    }


    fun parseInvite(code: String): InvitePayload? =
        (parseInviteDetailed(code) as? InviteParseResult.Ok)?.payload

    fun parseInviteDetailed(code: String): InviteParseResult {
        val stripped = code.replace(Regex("[^A-Za-z0-9]"), "").uppercase()
        if (!stripped.startsWith("STADE2")) {
            return InviteParseResult.MissingPrefix(stripped.take(8))
        }
        val b32 = stripped.substring(6)
        val cleanB32 = b32.filter { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" }
        val raw = try {
            Encoding.fromBase32(cleanB32)
        } catch (e: Throwable) {
            return InviteParseResult.DecodeError(e.message ?: e::class.simpleName ?: "decode")
        }
        return parseRaw(raw)
    }

    private fun parseRaw(raw: ByteArray): InviteParseResult {
        val minSize = 6 + 2 + 32 + 32 + 1184 + 1952 + 2 + 64 + DSA_SIG_LEN
        if (raw.size < minSize) return InviteParseResult.TooShort(minSize, raw.size)
        var off = 0
        for (i in 0 until 4) if (raw[off + i] != magic[i]) return InviteParseResult.BadMagic
        off += 4
        if (raw[off] != version) return InviteParseResult.BadVersion(raw[off].toInt() and 0xff)
        off += 1
        off += 1
        val nickLen = readU16(raw, off); off += 2
        if (nickLen < 0 || nickLen > 1024 || off + nickLen > raw.size) {
            return InviteParseResult.BadNickname(nickLen)
        }
        val nickname = raw.copyOfRange(off, off + nickLen).decodeToString()
        off += nickLen
        if (off + 32 + 32 + 1184 + 1952 + 2 > raw.size) {
            return InviteParseResult.TooShort(off + 32 + 32 + 1184 + 1952 + 2, raw.size)
        }
        val edPub = raw.copyOfRange(off, off + 32); off += 32
        val xPub = raw.copyOfRange(off, off + 32); off += 32
        val kemPub = raw.copyOfRange(off, off + 1184); off += 1184
        val dsaPub = raw.copyOfRange(off, off + 1952); off += 1952
        val addrLen = readU16(raw, off); off += 2
        if (addrLen < 0 || addrLen > 64 * 1024 || off + addrLen > raw.size) {
            return InviteParseResult.BadAddressBlob(addrLen)
        }
        val addrBlob = raw.copyOfRange(off, off + addrLen)
        off += addrLen
        if (off + 64 + DSA_SIG_LEN > raw.size) {
            return InviteParseResult.TooShort(off + 64 + DSA_SIG_LEN, raw.size)
        }
        val edSig = raw.copyOfRange(off, off + 64); off += 64
        val dsaSig = raw.copyOfRange(off, off + DSA_SIG_LEN); off += DSA_SIG_LEN

        if (off != raw.size) return InviteParseResult.TrailingBytes(raw.size - off)

        val tbs = raw.copyOfRange(0, raw.size - 64 - DSA_SIG_LEN)
        if (!crypto.verify(edPub, tbs, edSig)) return InviteParseResult.EdVerifyFail
        if (!pq.verifyMlDsa(dsaPub, tbs, dsaSig)) return InviteParseResult.MlDsaVerifyFail

        val addrs = if (addrLen == 0) emptyList()
        else addrBlob.decodeToString().split('\n').filter { it.isNotBlank() }
        val stadeId = StadeId.derive(edPub, dsaPub, crypto::hash)
        return InviteParseResult.Ok(
            InvitePayload(
                stadeId = stadeId,
                nickname = nickname,
                signingPublicKey = edPub,
                handshakePublicKey = xPub,
                mlkemPublicKey = kemPub,
                mldsaPublicKey = dsaPub,
                addresses = addrs
            )
        )
    }

    companion object {
        const val DSA_SIG_LEN: Int = 3309
    }


    fun deriveRootKey(
        owner: LocalIdentity,
        peer: InvitePayload,
        ownEphemeral: HandshakeEphemeral,
        peerEphemeral: PeerEphemeral,
        kemCiphertext: ByteArray,
        kemSharedSecret: ByteArray
    ): ByteArray {
        val ownerId = owner.stadeId.encodeToByteArray()
        val peerId = peer.stadeId.encodeToByteArray()
        val ownerIsLo = compareLex(ownerId, peerId) <= 0

        val dhStatic = crypto.keyAgreement(owner.privateHandshakeKey, peer.handshakePublicKey)
        val dhEphemeral = crypto.keyAgreement(ownEphemeral.dh.privateKey, peerEphemeral.dhPub)
        val dhStaticToPeerEph = crypto.keyAgreement(owner.privateHandshakeKey, peerEphemeral.dhPub)
        val dhEphToPeerStatic = crypto.keyAgreement(ownEphemeral.dh.privateKey, peer.handshakePublicKey)
        val (crossLo, crossHi) =
            if (ownerIsLo) dhStaticToPeerEph to dhEphToPeerStatic
            else dhEphToPeerStatic to dhStaticToPeerEph

        val (lo, hi) = if (ownerIsLo) ownerId to peerId else peerId to ownerId
        val (loSigningPub, hiSigningPub) =
            if (ownerIsLo) owner.publicSigningKey to peer.signingPublicKey
            else peer.signingPublicKey to owner.publicSigningKey
        val (loHandshakePub, hiHandshakePub) =
            if (ownerIsLo) owner.publicHandshakeKey to peer.handshakePublicKey
            else peer.handshakePublicKey to owner.publicHandshakeKey
        val (loMlKemPub, hiMlKemPub) =
            if (ownerIsLo) owner.publicMlKemKey to peer.mlkemPublicKey
            else peer.mlkemPublicKey to owner.publicMlKemKey
        val (loEphDhPub, hiEphDhPub) =
            if (ownerIsLo) ownEphemeral.dh.publicKey to peerEphemeral.dhPub
            else peerEphemeral.dhPub to ownEphemeral.dh.publicKey
        val (loEphKemPub, hiEphKemPub) =
            if (ownerIsLo) ownEphemeral.kem.publicKey to peerEphemeral.kemPub
            else peerEphemeral.kemPub to ownEphemeral.kem.publicKey

        val transcript = "stade-pqxdh-v3".encodeToByteArray() +
            lo + hi +
            loSigningPub + hiSigningPub +
            loHandshakePub + hiHandshakePub +
            loMlKemPub + hiMlKemPub +
            loEphDhPub + hiEphDhPub +
            loEphKemPub + hiEphKemPub +
            kemCiphertext
        val salt = crypto.hash(transcript)
        val secret = dhStatic + dhEphemeral + crossLo + crossHi + kemSharedSecret
        return crypto.hkdf(secret, salt, "stade-pqxdh-root-v3".encodeToByteArray(), 32)
    }

    fun newEphemeral(): HandshakeEphemeral =
        HandshakeEphemeral(crypto.generateAgreementKeyPair(), pq.generateMlKemKeyPair())

    fun encapsulateForPeer(peerEphemeralKemPub: ByteArray): KemResult =
        pq.mlkemEncapsulate(peerEphemeralKemPub)

    fun decapsulate(ownEphemeral: HandshakeEphemeral, ciphertext: ByteArray): ByteArray =
        pq.mlkemDecapsulate(ownEphemeral.kem.privateKey, ciphertext)

    fun isAlice(owner: LocalIdentity, peer: InvitePayload): Boolean =
        compareLex(owner.stadeId.encodeToByteArray(), peer.stadeId.encodeToByteArray()) < 0


    private fun composeTbs(
        nickname: String,
        edPub: ByteArray,
        xPub: ByteArray,
        kemPub: ByteArray,
        dsaPub: ByteArray,
        addresses: List<String>
    ): ByteArray {
        val nick = nickname.encodeToByteArray()
        require(nick.size <= 1024) { "nickname too long" }
        require(edPub.size == 32) { "Ed25519 public key has the wrong size" }
        require(xPub.size == 32) { "X25519 public key has the wrong size" }
        require(kemPub.size == 1184) { "ML-KEM public key has the wrong size: ${kemPub.size}" }
        require(dsaPub.size == 1952) { "ML-DSA public key has the wrong size: ${dsaPub.size}" }
        val addrBytes = addresses.joinToString("\n").encodeToByteArray()
        require(addrBytes.size <= 64 * 1024) { "addresses too long" }

        val total = 4 + 1 + 1 + 2 + nick.size + 32 + 32 + 1184 + 1952 + 2 + addrBytes.size
        val out = ByteArray(total)
        var o = 0
        magic.copyInto(out, o); o += 4
        out[o++] = version
        out[o++] = 0
        writeU16(out, o, nick.size); o += 2
        nick.copyInto(out, o); o += nick.size
        edPub.copyInto(out, o); o += 32
        xPub.copyInto(out, o); o += 32
        kemPub.copyInto(out, o); o += 1184
        dsaPub.copyInto(out, o); o += 1952
        writeU16(out, o, addrBytes.size); o += 2
        addrBytes.copyInto(out, o); o += addrBytes.size
        check(o == total) { "TBS length mismatch: $o != $total" }
        return out
    }

    private fun writeU16(buf: ByteArray, off: Int, v: Int) {
        buf[off] = ((v ushr 8) and 0xff).toByte()
        buf[off + 1] = (v and 0xff).toByte()
    }
    private fun readU16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xff) shl 8) or (buf[off + 1].toInt() and 0xff)

    private fun compareLex(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a[i].toInt() and 0xff
            val y = b[i].toInt() and 0xff
            if (x != y) return x - y
        }
        return a.size - b.size
    }
}
