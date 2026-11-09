package dev.stade.group

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.stade.crypto.platformCrypto
import dev.stade.crypto.platformPq
import dev.stade.db.StadeDb
import dev.stade.identity.LocalIdentity
import dev.stade.identity.StadeId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val crypto = platformCrypto()
private val pq = platformPq()

private class Party(val nickname: String) {
    val sign = crypto.generateSigningKeyPair()
    val dsa = pq.generateMlDsaKeyPair()
    val id: String = StadeId.derive(sign.publicKey, dsa.publicKey, crypto::hash)
    val entry = GroupMemberEntry(id, nickname, sign.publicKey, dsa.publicKey)
    fun identity(): LocalIdentity = LocalIdentity(
        id = id,
        nickname = nickname,
        publicSigningKey = sign.publicKey,
        privateSigningKey = sign.privateKey,
        publicHandshakeKey = ByteArray(32),
        privateHandshakeKey = ByteArray(32),
        publicMlKemKey = ByteArray(1184),
        privateMlKemKey = ByteArray(32),
        publicMlDsaKey = dsa.publicKey,
        privateMlDsaKey = dsa.privateKey,
        createdAt = 0L
    )
}

private fun withGroups(body: (GroupManager) -> Unit) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    try {
        StadeDb.Schema.create(driver)
        body(GroupManager(StadeDb(driver), crypto, pq))
    } finally {
        driver.close()
    }
}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
class GroupMembershipAuthorityTest {

    @Test
    fun anOrdinaryMemberCannotAddAnOutsider() = withGroups { groups ->
        val creator = Party("creator")
        val member = Party("member")
        val outsider = Party("outsider")
        val group = groups.createGroup("local", creator.id, "audit")
        groups.setMemberIdentity(group.id, creator.entry)
        groups.setMemberIdentity(group.id, member.entry)

        val forged = groups.encodeRoster(
            member.identity(), group.id, group.name,
            listOf(creator.entry, member.entry, outsider.entry)
        )
        assertNull(forged, "only the creator can produce a roster")

        val handmade = "$GRP_ROSTER_PREFIX${group.id}:1:AAAA:AAAA:audit\n" +
            listOf(creator.entry, member.entry, outsider.entry).joinToString("\n") {
                "${it.memberId}\u001f${it.nickname}\u001f" +
                    kotlin.io.encoding.Base64.Default.encode(it.signingKey!!) + "\u001f" +
                    kotlin.io.encoding.Base64.Default.encode(it.mldsaKey!!)
            }
        groups.handleRoster("local", member.id, handmade)

        assertFalse(
            groups.isMember(group.id, outsider.id),
            "an unsigned roster from an ordinary member must not add anyone"
        )
    }

    @Test
    fun theCreatorsRosterIsAccepted() = withGroups { groups ->
        val creator = Party("creator")
        val joiner = Party("joiner")
        val group = groups.createGroup("local", creator.id, "audit")
        groups.setMemberIdentity(group.id, creator.entry)

        val signed = groups.encodeRoster(
            creator.identity(), group.id, group.name,
            listOf(creator.entry, joiner.entry)
        )
        assertNotNull(signed, "the creator must be able to publish a roster")
        assertNotNull(groups.handleRoster("local", creator.id, signed))

        assertTrue(groups.isMember(group.id, joiner.id), "a creator-signed roster adds members")
    }

    @Test
    fun aRemovedMemberCannotBeRevivedByReplayingAnOldRoster() = withGroups { groups ->
        val creator = Party("creator")
        val kicked = Party("kicked")
        val group = groups.createGroup("local", creator.id, "audit")
        groups.setMemberIdentity(group.id, creator.entry)

        val before = groups.encodeRoster(
            creator.identity(), group.id, group.name,
            listOf(creator.entry, kicked.entry)
        )
        assertNotNull(before)
        groups.handleRoster("local", creator.id, before)
        assertTrue(groups.isMember(group.id, kicked.id))

        val epoch = groups.kickMemberAsCreator(group.id, kicked.id)
        assertNotNull(epoch, "the kick must advance the membership epoch")
        assertFalse(groups.isMember(group.id, kicked.id))

        groups.handleRoster("local", creator.id, before)

        assertFalse(
            groups.isMember(group.id, kicked.id),
            "replaying the roster from before the kick must not bring the member back"
        )
    }
}
