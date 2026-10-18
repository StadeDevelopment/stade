package dev.stade.identity

import kotlinx.serialization.Serializable

@Serializable
data class LocalIdentity(
    val id: String,
    val nickname: String,
    val publicSigningKey: ByteArray,
    val privateSigningKey: ByteArray,
    val publicHandshakeKey: ByteArray,
    val privateHandshakeKey: ByteArray,
    val publicMlKemKey: ByteArray,
    val privateMlKemKey: ByteArray,
    val publicMlDsaKey: ByteArray,
    val privateMlDsaKey: ByteArray,
    val createdAt: Long,
    val avatar: ByteArray? = null,
    val bio: String = "",
    val status: String = ""
) {
    val stadeId: String get() = id
    override fun equals(other: Any?): Boolean =
        other is LocalIdentity && other.id == id && other.nickname == nickname &&
            other.avatar.contentEquals(avatar) && other.bio == bio && other.status == status
    override fun hashCode(): Int = id.hashCode()
}

@Serializable
data class RemoteIdentity(
    val id: String,
    val nickname: String,
    val publicSigningKey: ByteArray,
    val publicHandshakeKey: ByteArray,
    val publicMlKemKey: ByteArray,
    val publicMlDsaKey: ByteArray
) {
    val stadeId: String get() = id
    override fun equals(other: Any?): Boolean = other is RemoteIdentity && other.id == id
    override fun hashCode(): Int = id.hashCode()
}
