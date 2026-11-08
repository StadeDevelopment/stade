package dev.stade.transport

class ExpectedPeer(
    val stadeId: String,
    val signingKey: ByteArray,
    val mldsaKey: ByteArray
) {
    fun matches(peerStadeId: String, peerSigningKey: ByteArray, peerMldsaKey: ByteArray): Boolean =
        stadeId == peerStadeId &&
            signingKey.contentEquals(peerSigningKey) &&
            mldsaKey.contentEquals(peerMldsaKey)
}
