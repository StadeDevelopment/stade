package dev.stade.sync

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object ByteArrayAsBase64Serializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ByteArrayAsBase64", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ByteArray) = encoder.encodeString(Base64.Default.encode(value))
    override fun deserialize(decoder: Decoder): ByteArray = Base64.Default.decode(decoder.decodeString())
}

enum class RecordType(val code: Byte) {
    HELLO(1),
    AUTH(2),
    MESSAGE(3),
    ACK(4),
    PING(5),
    BYE(6),

    KEM_OFFER(7),
    MESSAGE_BIN(8);

    companion object {
        fun fromCode(c: Byte): RecordType? = entries.firstOrNull { it.code == c }
    }
}

@Serializable
data class HelloPayload(
    val protocolVersion: Int,
    val stadeId: String,
    val nickname: String,
    val signingPublicKey: ByteArray,
    val handshakePublicKey: ByteArray,
    val mlkemPublicKey: ByteArray,
    val mldsaPublicKey: ByteArray,
    val nonce: ByteArray,
    val transcriptCommitment: ByteArray,
    val addresses: List<String> = emptyList(),
    val reAddRequest: Boolean = false,
    val groupProtocol: Int = 1,
    val wireProtocol: Int = 1,
    val ephemeralHandshakeKey: ByteArray = ByteArray(0),
    val ephemeralMlKemKey: ByteArray = ByteArray(0)
)

@Serializable
data class AuthPayload(
    val stadeId: String,
    val edSignature: ByteArray,
    val mldsaSignature: ByteArray,
    val isStadiumJoin: Boolean = false,
    val noExistingContact: Boolean = false
)

@Serializable
data class KemOfferPayload(
    val ciphertext: ByteArray
)

@Serializable
data class MessagePayload(
    val messageId: String,
    val timestamp: Long,
    @Serializable(with = ByteArrayAsBase64Serializer::class)
    val ratchetFrame: ByteArray
)

@Serializable
data class AckPayload(val messageId: String)

const val WIRE_PROTOCOL_VERSION = 2
private const val BIN_PAYLOAD_VERSION: Byte = 1

fun encodeBinaryPayload(payload: MessagePayload): ByteArray? {
    val id = payload.messageId.encodeToByteArray()
    if (id.size > 255) return null
    val out = ByteArray(2 + id.size + 8 + payload.ratchetFrame.size)
    out[0] = BIN_PAYLOAD_VERSION
    out[1] = id.size.toByte()
    id.copyInto(out, 2)
    var off = 2 + id.size
    for (i in 0 until 8) {
        out[off + i] = ((payload.timestamp ushr (56 - 8 * i)) and 0xff).toByte()
    }
    off += 8
    payload.ratchetFrame.copyInto(out, off)
    return out
}

fun decodeBinaryPayload(bytes: ByteArray): MessagePayload? {
    if (bytes.size < 10) return null
    if (bytes[0] != BIN_PAYLOAD_VERSION) return null
    val idLen = bytes[1].toInt() and 0xff
    if (idLen == 0 || bytes.size < 2 + idLen + 8) return null
    val id = bytes.copyOfRange(2, 2 + idLen).decodeToString()
    var off = 2 + idLen
    var timestamp = 0L
    for (i in 0 until 8) {
        timestamp = (timestamp shl 8) or (bytes[off + i].toLong() and 0xff)
    }
    off += 8
    return MessagePayload(id, timestamp, bytes.copyOfRange(off, bytes.size))
}

@Serializable
data class SyncRecord(
    val type: RecordType,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        other is SyncRecord && other.type == type && other.payload.contentEquals(payload)
    override fun hashCode(): Int = type.hashCode() * 31 + payload.contentHashCode()
}

const val FRAME_HEADER_BYTES = 5

const val LINK_SEAL_OVERHEAD_BYTES = 16

const val MAX_WIRE_FRAME_BYTES =
    4 * 1024 * 1024 + FRAME_HEADER_BYTES + LINK_SEAL_OVERHEAD_BYTES

object FrameCodec {
    const val MAX_LEN = 4 * 1024 * 1024

    fun encode(record: SyncRecord): ByteArray {
        val len = record.payload.size
        require(len in 0..MAX_LEN) { "frame too large" }
        val out = ByteArray(5 + len)
        out[0] = record.type.code
        out[1] = ((len ushr 24) and 0xff).toByte()
        out[2] = ((len ushr 16) and 0xff).toByte()
        out[3] = ((len ushr 8) and 0xff).toByte()
        out[4] = (len and 0xff).toByte()
        record.payload.copyInto(out, 5)
        return out
    }

    fun decode(frame: ByteArray): SyncRecord? {
        if (frame.size < 5) return null
        val type = RecordType.fromCode(frame[0]) ?: return null
        val len = ((frame[1].toInt() and 0xff) shl 24) or
            ((frame[2].toInt() and 0xff) shl 16) or
            ((frame[3].toInt() and 0xff) shl 8) or
            (frame[4].toInt() and 0xff)
        if (len !in 0..MAX_LEN) return null
        if (frame.size < 5 + len) return null
        return SyncRecord(type, frame.copyOfRange(5, 5 + len))
    }
}
