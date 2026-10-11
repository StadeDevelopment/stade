package dev.stade.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupFormatVersionTest {

    private val rng = SecureRandom()
    private val magic = "STDBK1".toByteArray()

    private fun payloadOf(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        entries.forEach { (name, data) ->
            val n = name.toByteArray()
            out.write(ByteBuffer.allocate(2).putShort(n.size.toShort()).array())
            out.write(n)
            out.write(ByteBuffer.allocate(8).putLong(data.size.toLong()).array())
            out.write(data)
        }
        return out.toByteArray()
    }

    private fun writeLegacyV1(passphrase: String, entries: List<Pair<String, ByteArray>>): ByteArray {
        val iterations = 310_000
        val chunkSize = 1 shl 20
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val prefix = ByteArray(8).also { rng.nextBytes(it) }
        val payload = payloadOf(entries)

        val header = ByteBuffer.allocate(6 + 1 + 16 + 4 + 8 + 8 + 4).apply {
            put(magic)
            put(1)
            put(salt)
            putInt(iterations)
            put(prefix)
            putLong(payload.size.toLong())
            putInt(chunkSize)
        }.array()

        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()

        val out = ByteArrayOutputStream()
        out.write(header)
        var offset = 0
        var index = 0
        while (offset < payload.size) {
            val want = minOf(chunkSize, payload.size - offset)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val nonce = ByteBuffer.allocate(12).put(prefix).putInt(index).array()
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(ByteBuffer.allocate(header.size + 4).put(header).putInt(index).array())
            out.write(cipher.doFinal(payload, offset, want))
            offset += want
            index++
        }
        return out.toByteArray()
    }

    private fun writeCurrent(passphrase: String, entries: List<Pair<String, ByteArray>>): ByteArray {
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

    @Test
    fun `a legacy pbkdf2 backup still restores`() {
        val entries = listOf("vault" to "META".toByteArray(), "db" to "DATABASE".toByteArray())
        val archive = writeLegacyV1("correct horse", entries)
        assertEquals(1, archive[6].toInt(), "fixture must be a version 1 archive")

        val scratch = Files.createTempFile("stade-legacy", ".bin").toFile()
        try {
            val outcome = BackupArchive.read(
                ByteArrayInputStream(archive), "correct horse".toCharArray(), scratch
            )
            val ok = assertIs<BackupReadOutcome.Ok>(outcome, "a v1 archive must still open")
            assertEquals(listOf("vault", "db"), ok.entries.map { it.name })
            assertEquals(8L, ok.entries.first { it.name == "db" }.length)
        } finally {
            scratch.delete()
        }
    }

    @Test
    fun `a legacy backup with the wrong passphrase is still reported as wrong`() {
        val archive = writeLegacyV1("right", listOf("vault" to "X".toByteArray()))
        val scratch = Files.createTempFile("stade-legacy", ".bin").toFile()
        try {
            assertIs<BackupReadOutcome.WrongPassphrase>(
                BackupArchive.read(ByteArrayInputStream(archive), "wrong".toCharArray(), scratch)
            )
        } finally {
            scratch.delete()
        }
    }

    @Test
    fun `newly written backups use the argon2 format`() {
        val archive = writeCurrent("pass phrase", listOf("vault" to "M".toByteArray()))
        assertEquals(2, archive[6].toInt(), "new archives must be written at version 2")
    }

    @Test
    fun `a newly written backup round trips`() {
        val entries = listOf("vault" to "META".toByteArray(), "db" to "DATABASE".toByteArray())
        val archive = writeCurrent("pass phrase", entries)
        val scratch = Files.createTempFile("stade-new", ".bin").toFile()
        try {
            val ok = assertIs<BackupReadOutcome.Ok>(
                BackupArchive.read(ByteArrayInputStream(archive), "pass phrase".toCharArray(), scratch)
            )
            assertEquals(listOf("vault", "db"), ok.entries.map { it.name })
        } finally {
            scratch.delete()
        }
    }

    @Test
    fun `an archive from a future version is reported as such rather than as corrupt`() {
        val archive = writeCurrent("pass phrase", listOf("vault" to "M".toByteArray()))
        archive[6] = 99
        val scratch = Files.createTempFile("stade-future", ".bin").toFile()
        try {
            assertIs<BackupReadOutcome.NewerVersion>(
                BackupArchive.read(ByteArrayInputStream(archive), "pass phrase".toCharArray(), scratch)
            )
        } finally {
            scratch.delete()
        }
    }

    @Test
    fun `a file that is not a backup is rejected`() {
        val scratch = Files.createTempFile("stade-junk", ".bin").toFile()
        try {
            assertIs<BackupReadOutcome.NotABackup>(
                BackupArchive.read(
                    ByteArrayInputStream("definitely not a stade backup at all".toByteArray()),
                    "x".toCharArray(),
                    scratch
                )
            )
        } finally {
            scratch.delete()
        }
    }
}
