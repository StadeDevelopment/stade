package dev.stade.group

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.PqCrypto
import dev.stade.crypto.platformCrypto
import dev.stade.crypto.platformPq
import dev.stade.db.StadeDb
import dev.stade.identity.StadeId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class CollidingHashCrypto(real: CryptoApi) : CryptoApi by real {
    override fun hash(data: ByteArray): ByteArray = ByteArray(32) { 0x07 }
}

class GroupRosterIdentityTest {

    private val crypto: CryptoApi = platformCrypto()
    private val pq: PqCrypto = platformPq()

    private fun manager(api: CryptoApi): Pair<GroupManager, JdbcSqliteDriver> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        StadeDb.Schema.create(driver)
        return GroupManager(StadeDb(driver), api, pq) to driver
    }

    private fun keys(): Pair<ByteArray, ByteArray> {
        val signing = crypto.generateSigningKeyPair()
        val dsa = pq.generateMlDsaKeyPair()
        return signing.publicKey to dsa.publicKey
    }

    @Test
    fun `a roster entry whose keys do not derive its member id is refused`() {
        val (groups, driver) = manager(crypto)
        try {
            val group = groups.createGroup("owner", "STADE-0000-0000-0000", "g")
            val (signing, dsa) = keys()
            val (otherSigning, otherDsa) = keys()
            val otherId = StadeId.derive(otherSigning, otherDsa, crypto::hash)

            assertFalse(
                groups.setMemberIdentity(group.id, GroupMemberEntry(otherId, "mallory", signing, dsa)),
                "keys that do not derive the claimed member id must be refused"
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun `colliding identity keys cannot replace an established member`() {
        val colliding = CollidingHashCrypto(crypto)
        val (groups, driver) = manager(colliding)
        try {
            val group = groups.createGroup("owner", "STADE-0000-0000-0000", "g")
            val (victimSigning, victimDsa) = keys()
            val (attackerSigning, attackerDsa) = keys()

            val memberId = StadeId.derive(victimSigning, victimDsa, colliding::hash)
            assertEquals(
                memberId,
                StadeId.derive(attackerSigning, attackerDsa, colliding::hash),
                "this test requires both key sets to derive the same member id"
            )

            assertTrue(
                groups.setMemberIdentity(
                    group.id,
                    GroupMemberEntry(memberId, "victim", victimSigning, victimDsa)
                )
            )

            assertFalse(
                groups.setMemberIdentity(
                    group.id,
                    GroupMemberEntry(memberId, "victim", attackerSigning, attackerDsa)
                ),
                "a colliding Stade ID must not be able to replace stored identity keys"
            )

            val stored = groups.memberIdentity(group.id, memberId)
            assertNotNull(stored)
            assertContentEquals(victimSigning, stored.signingKey)
            assertContentEquals(victimDsa, stored.mldsaKey)
        } finally {
            driver.close()
        }
    }

    @Test
    fun `re-announcing the same identity keys still updates the nickname`() {
        val (groups, driver) = manager(crypto)
        try {
            val group = groups.createGroup("owner", "STADE-0000-0000-0000", "g")
            val (signing, dsa) = keys()
            val memberId = StadeId.derive(signing, dsa, crypto::hash)

            assertTrue(groups.setMemberIdentity(group.id, GroupMemberEntry(memberId, "first", signing, dsa)))
            assertTrue(groups.setMemberIdentity(group.id, GroupMemberEntry(memberId, "second", signing, dsa)))

            val stored = groups.memberIdentity(group.id, memberId)
            assertNotNull(stored)
            assertEquals("second", stored.nickname)
            assertContentEquals(signing, stored.signingKey)
        } finally {
            driver.close()
        }
    }
}
