package dev.stade.identity

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.PqCrypto
import dev.stade.db.StadeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class IdentityManager(
    private val db: StadeDb,
    private val crypto: CryptoApi,
    private val pq: PqCrypto
) {

    fun observeIdentities(): Flow<List<LocalIdentity>> =
        db.stadeDbQueries.selectAllIdentities()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun create(nickname: String): LocalIdentity {
        val signing = crypto.generateSigningKeyPair()
        val handshake = crypto.generateAgreementKeyPair()
        val mlkem = pq.generateMlKemKeyPair()
        val mldsa = pq.generateMlDsaKeyPair()
        val id = StadeId.derive(signing.publicKey, mldsa.publicKey, crypto::hash)
        val now = Clock.System.now().toEpochMilliseconds()
        db.stadeDbQueries.insertIdentity(
            id, nickname,
            signing.publicKey, signing.privateKey,
            handshake.publicKey, handshake.privateKey,
            mlkem.publicKey, mlkem.privateKey,
            mldsa.publicKey, mldsa.privateKey,
            now, null
        )
        return LocalIdentity(
            id = id,
            nickname = nickname,
            publicSigningKey = signing.publicKey,
            privateSigningKey = signing.privateKey,
            publicHandshakeKey = handshake.publicKey,
            privateHandshakeKey = handshake.privateKey,
            publicMlKemKey = mlkem.publicKey,
            privateMlKemKey = mlkem.privateKey,
            publicMlDsaKey = mldsa.publicKey,
            privateMlDsaKey = mldsa.privateKey,
            createdAt = now
        )
    }

    fun get(id: String): LocalIdentity? =
        db.stadeDbQueries.selectIdentity(id).executeAsOneOrNull()?.toDomain()

    fun setAvatar(identityId: String, avatar: ByteArray?) {
        db.stadeDbQueries.setIdentityAvatar(avatar, identityId)
    }

    fun setProfile(identityId: String, bio: String, status: String) {
        db.stadeDbQueries.setIdentityProfile(bio, status, identityId)
    }

    private fun dev.stade.db.LocalIdentity.toDomain(): LocalIdentity =
        LocalIdentity(
            id = id,
            nickname = nickname,
            publicSigningKey = publicKey,
            privateSigningKey = privateKey,
            publicHandshakeKey = handshakePublicKey,
            privateHandshakeKey = handshakePrivateKey,
            publicMlKemKey = mlkemPublicKey,
            privateMlKemKey = mlkemPrivateKey,
            publicMlDsaKey = mldsaPublicKey,
            privateMlDsaKey = mldsaPrivateKey,
            createdAt = createdAt,
            avatar = avatar,
            bio = bio,
            status = status
        )
}
