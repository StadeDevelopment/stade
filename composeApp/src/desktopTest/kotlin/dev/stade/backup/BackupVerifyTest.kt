package dev.stade.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupVerifyTest {

    private fun archive(passphrase: String, entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        BackupArchive.write(
            out,
            passphrase.toCharArray(),
            entries.map { (name, data) ->
                BackupSource(name, data.size.toLong()) { ByteArrayInputStream(data) }
            }
        )
        return out.toByteArray()
    }

    private fun sampleEntries(dbSize: Int = 5000) = listOf(
        "vault" to ByteArray(212) { (it % 251).toByte() },
        "db" to ByteArray(dbSize) { (it % 97).toByte() },
        "onion" to "ONIONKEY".toByteArray()
    )

    @Test
    fun `a sound archive verifies and lists its entries`() {
        val data = archive("pass phrase", sampleEntries())
        val ok = assertIs<BackupVerifyOutcome.Ok>(
            BackupArchive.verify(ByteArrayInputStream(data), "pass phrase".toCharArray())
        )
        assertEquals(listOf("vault", "db", "onion"), ok.entries.map { it.name })
        assertEquals(212L, ok.entries.first { it.name == "vault" }.length)
        assertEquals(5000L, ok.entries.first { it.name == "db" }.length)
        assertTrue(ok.payloadBytes > 5000L)
    }

    @Test
    fun `verification reports a wrong passphrase`() {
        val data = archive("right one", sampleEntries())
        assertIs<BackupVerifyOutcome.WrongPassphrase>(
            BackupArchive.verify(ByteArrayInputStream(data), "wrong one".toCharArray())
        )
    }

    @Test
    fun `a single flipped byte in the payload is detected`() {
        val data = archive("pass phrase", sampleEntries())
        val target = data.size - 40
        data[target] = (data[target].toInt() xor 0x01).toByte()
        val outcome = BackupArchive.verify(ByteArrayInputStream(data), "pass phrase".toCharArray())
        assertTrue(
            outcome is BackupVerifyOutcome.Damaged || outcome is BackupVerifyOutcome.WrongPassphrase,
            "a corrupted archive must not verify, got $outcome"
        )
    }

    @Test
    fun `a flipped byte in the header is detected`() {
        val data = archive("pass phrase", sampleEntries())
        data[20] = (data[20].toInt() xor 0x01).toByte()
        val outcome = BackupArchive.verify(ByteArrayInputStream(data), "pass phrase".toCharArray())
        assertTrue(
            outcome !is BackupVerifyOutcome.Ok,
            "a tampered header must not verify, got $outcome"
        )
    }

    @Test
    fun `a truncated archive is detected`() {
        val data = archive("pass phrase", sampleEntries())
        val cut = data.copyOfRange(0, data.size - 100)
        assertIs<BackupVerifyOutcome.Damaged>(
            BackupArchive.verify(ByteArrayInputStream(cut), "pass phrase".toCharArray())
        )
    }

    @Test
    fun `a file that is not a backup is rejected`() {
        assertIs<BackupVerifyOutcome.NotABackup>(
            BackupArchive.verify(ByteArrayInputStream("hello world".toByteArray()), "x".toCharArray())
        )
    }

    @Test
    fun `verification spanning several chunks still indexes correctly`() {
        val big = BackupArchive.CHUNK_SIZE * 2 + 1234
        val data = archive("pass phrase", sampleEntries(dbSize = big))
        val ok = assertIs<BackupVerifyOutcome.Ok>(
            BackupArchive.verify(ByteArrayInputStream(data), "pass phrase".toCharArray())
        )
        assertEquals(big.toLong(), ok.entries.first { it.name == "db" }.length)
        assertEquals(listOf("vault", "db", "onion"), ok.entries.map { it.name })
    }

    @Test
    fun `a backup missing the database entry is reported incomplete`() {
        val data = archive("pass phrase", listOf("vault" to ByteArray(212)))
        val outcome = StadeBackup.verify(ByteArrayInputStream(data), "pass phrase".toCharArray())
        val failed = assertIs<VerifyOutcome.Failed>(outcome)
        assertEquals(RestoreFailure.Incomplete, failed.reason)
    }

    @Test
    fun `a complete backup reports whether the onion key is present`() {
        val withOnion = StadeBackup.verify(
            ByteArrayInputStream(archive("pass phrase", sampleEntries())), "pass phrase".toCharArray()
        )
        assertTrue(assertIs<VerifyOutcome.Valid>(withOnion).onionKeyIncluded)

        val withoutOnion = StadeBackup.verify(
            ByteArrayInputStream(
                archive("pass phrase", listOf("vault" to ByteArray(212), "db" to ByteArray(64)))
            ),
            "pass phrase".toCharArray()
        )
        assertTrue(!assertIs<VerifyOutcome.Valid>(withoutOnion).onionKeyIncluded)
    }
}
