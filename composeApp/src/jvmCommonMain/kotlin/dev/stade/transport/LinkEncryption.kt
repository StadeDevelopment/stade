package dev.stade.transport

import dev.stade.crypto.CryptoApi
import dev.stade.crypto.platformCrypto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private const val KX_MAGIC: Byte = 0xFE.toByte()
private const val KX_VERSION: Byte = 0x01
private const val KX_PUB_LEN = 32
private const val KX_FRAME_LEN = 2 + KX_PUB_LEN
private const val NEGOTIATE_TIMEOUT_MS = 10_000L
private val LINK_INFO = "stade-link-v1".encodeToByteArray()

internal class LinkEncryption(
    private val crypto: CryptoApi,
    private val sendKey: ByteArray,
    private val recvKey: ByteArray
) {
    private var sendCounter = 0L
    private var recvCounter = 0L

    fun seal(plaintext: ByteArray): ByteArray =
        crypto.aeadSeal(sendKey, nonce(sendCounter++), plaintext, ByteArray(0))

    fun open(ciphertext: ByteArray): ByteArray? =
        crypto.aeadOpen(recvKey, nonce(recvCounter++), ciphertext, ByteArray(0))

    private fun nonce(counter: Long): ByteArray {
        val out = ByteArray(12)
        for (i in 0 until 8) {
            out[4 + i] = ((counter ushr (56 - 8 * i)) and 0xff).toByte()
        }
        return out
    }

    companion object {
        fun keyExchangeFrame(publicKey: ByteArray): ByteArray {
            val out = ByteArray(KX_FRAME_LEN)
            out[0] = KX_MAGIC
            out[1] = KX_VERSION
            publicKey.copyInto(out, 2)
            return out
        }

        fun peerKeyFrom(frame: ByteArray): ByteArray? {
            if (frame.size != KX_FRAME_LEN) return null
            if (frame[0] != KX_MAGIC || frame[1] != KX_VERSION) return null
            return frame.copyOfRange(2, KX_FRAME_LEN)
        }

        fun derive(
            crypto: CryptoApi,
            ownPublicKey: ByteArray,
            ownPrivateKey: ByteArray,
            peerPublicKey: ByteArray
        ): LinkEncryption? {
            val shared = runCatching {
                crypto.keyAgreement(ownPrivateKey, peerPublicKey)
            }.getOrNull() ?: return null
            val ownIsLo = compareLex(ownPublicKey, peerPublicKey) <= 0
            val salt = if (ownIsLo) ownPublicKey + peerPublicKey else peerPublicKey + ownPublicKey
            val keys = crypto.hkdf(shared, salt, LINK_INFO, 64)
            val lo = keys.copyOfRange(0, 32)
            val hi = keys.copyOfRange(32, 64)
            return if (ownIsLo) LinkEncryption(crypto, lo, hi) else LinkEncryption(crypto, hi, lo)
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
    }
}

internal class EncryptedLinkConnection(
    private val inner: Connection,
    private val crypto: CryptoApi = platformCrypto()
) : Connection {

    override val remoteAddress: String get() = inner.remoteAddress

    override val lastProgressAtMs: Long get() = inner.lastProgressAtMs

    private val negotiateLock = Mutex()
    private var negotiated = false
    private var link: LinkEncryption? = null
    private var buffered: ByteArray? = null

    override suspend fun send(frame: ByteArray) {
        negotiate()
        val active = link
        inner.send(if (active == null) frame else active.seal(frame))
    }

    override suspend fun receive(): ByteArray? {
        negotiate()
        buffered?.let {
            buffered = null
            return it
        }
        val raw = inner.receive() ?: return null
        val active = link ?: return raw
        return active.open(raw)
    }

    override suspend fun close() = inner.close()

    private suspend fun negotiate() {
        if (negotiated) return
        negotiateLock.withLock {
            if (negotiated) return
            negotiated = true
            val ephemeral = runCatching { crypto.generateAgreementKeyPair() }.getOrNull() ?: return
            runCatching {
                inner.send(LinkEncryption.keyExchangeFrame(ephemeral.publicKey))
            }.getOrElse { return }
            val first = withTimeoutOrNull(NEGOTIATE_TIMEOUT_MS) { inner.receive() } ?: return
            val peerKey = LinkEncryption.peerKeyFrom(first)
            if (peerKey == null) {
                buffered = first
                return
            }
            link = LinkEncryption.derive(
                crypto, ephemeral.publicKey, ephemeral.privateKey, peerKey
            )
        }
    }
}
