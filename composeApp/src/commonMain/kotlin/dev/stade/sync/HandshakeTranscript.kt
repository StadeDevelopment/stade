package dev.stade.sync

private val TRANSCRIPT_PREFIX = "stade-tc-v4".encodeToByteArray()
private val AUTH_MESSAGE_PREFIX = "stade-auth-v4".encodeToByteArray()

internal fun intBytes(value: Int): ByteArray {
    val out = ByteArray(4)
    out[0] = ((value ushr 24) and 0xff).toByte()
    out[1] = ((value ushr 16) and 0xff).toByte()
    out[2] = ((value ushr 8) and 0xff).toByte()
    out[3] = (value and 0xff).toByte()
    return out
}

internal fun lengthPrefixed(payload: ByteArray): ByteArray = intBytes(payload.size) + payload

fun authFlagBytes(isStadiumJoin: Boolean, noExistingContact: Boolean): ByteArray =
    byteArrayOf(
        if (isStadiumJoin) 1 else 0,
        if (noExistingContact) 1 else 0
    )

fun handshakeTranscriptBody(hello: HelloPayload): ByteArray =
    TRANSCRIPT_PREFIX +
        intBytes(hello.protocolVersion) +
        hello.signingPublicKey +
        hello.handshakePublicKey +
        hello.mlkemPublicKey +
        hello.mldsaPublicKey +
        hello.ephemeralHandshakeKey +
        hello.ephemeralMlKemKey +
        lengthPrefixed(hello.nickname.encodeToByteArray()) +
        intBytes(hello.addresses.size) +
        hello.addresses.fold(ByteArray(0)) { acc, address ->
            acc + lengthPrefixed(address.encodeToByteArray())
        } +
        byteArrayOf(if (hello.reAddRequest) 1 else 0) +
        intBytes(hello.groupProtocol) +
        intBytes(hello.wireProtocol)

fun authSignatureMessage(
    nonce: ByteArray,
    signerTranscript: ByteArray,
    peerTranscript: ByteArray,
    isStadiumJoin: Boolean,
    noExistingContact: Boolean
): ByteArray =
    AUTH_MESSAGE_PREFIX + nonce + signerTranscript + peerTranscript +
        authFlagBytes(isStadiumJoin, noExistingContact)
