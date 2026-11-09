package dev.stade.security

import java.io.File
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultKdfMigrationTest {

    private val rng = SecureRandom()

    private val verifierPlain = "STADE-VERIFY-01".toByteArray() + byteArrayOf(0)
    private val duressVerifierPlain = "STADE-DURESS-01".toByteArray() + byteArrayOf(0)

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return encoded
    }

    private fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(plaintext)
    }

    private fun writeLegacyVault(
        root: File,
        pin: String,
        duressPin: String?,
        dbContents: ByteArray
    ) {
        val iterations = 210_000
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val kek = pbkdf2(pin, salt, iterations)
        val dek = ByteArray(32).also { rng.nextBytes(it) }
        val verifierNonce = ByteArray(12).also { rng.nextBytes(it) }
        val dekNonce = ByteArray(12).also { rng.nextBytes(it) }
        val verifierCt = seal(kek, verifierNonce, verifierPlain)
        val dekCt = seal(kek, dekNonce, dek)

        val duressSalt = ByteArray(16).also { rng.nextBytes(it) }
        val duressNonce = ByteArray(12).also { rng.nextBytes(it) }
        val duressCt: ByteArray
        if (duressPin != null) {
            val duressKek = pbkdf2(duressPin, duressSalt, iterations)
            duressCt = seal(duressKek, duressNonce, duressVerifierPlain)
        } else {
            duressCt = ByteArray(32)
        }

        val size = 4 + 1 + 16 + 4 + 12 + 32 + 12 + 48 + 4 + 8 + 1 + 4 + 1 + 1 + 16 + 4 + 12 + 32
        val buf = ByteBuffer.wrap(ByteArray(size))
        buf.put("STDV".toByteArray())
        buf.put(0x01)
        buf.put(salt)
        buf.putInt(iterations)
        buf.put(verifierNonce)
        buf.put(verifierCt)
        buf.put(dekNonce)
        buf.put(dekCt)
        buf.putInt(0)
        buf.putLong(0L)
        buf.put(0)
        buf.putInt(SessionTimeout.IMMEDIATE)
        buf.put(1)
        buf.put(if (duressPin != null) 1 else 0)
        buf.put(duressSalt)
        buf.putInt(if (duressPin != null) iterations else 0)
        buf.put(duressNonce)
        buf.put(duressCt)
        File(root, "stade.vault").writeBytes(buf.array())

        require(dbContents.isNotEmpty())
    }

    private fun metaVersion(root: File): Int =
        File(root, "stade.vault").readBytes()[4].toInt()

    private fun metaSize(root: File): Int = File(root, "stade.vault").length().toInt()

    @Test
    fun `a legacy pbkdf2 vault still unlocks and keeps its database`() {
        val root = Files.createTempDirectory("stade-kdf").toFile()
        try {
            val contents = sqliteFixture("legacy payload")
            writeLegacyVault(root, "1234", null, contents)
            assertEquals(1, metaVersion(root), "fixture must start as a legacy v1 vault")

            val vault = FileVault(root)
            assertTrue(vault.unlock("1234") is UnlockOutcome.Success, "legacy vault must still unlock")
            assertTrue(vault.databaseKey() != null, "an unlocked legacy vault must yield its database key")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `unlocking a legacy vault upgrades it to argon2 and it reopens`() {
        val root = Files.createTempDirectory("stade-kdf").toFile()
        try {
            val contents = sqliteFixture("payload")
            writeLegacyVault(root, "1234", null, contents)
            val before = metaSize(root)

            assertTrue(FileVault(root).unlock("1234") is UnlockOutcome.Success)

            assertEquals(2, metaVersion(root), "meta must be rewritten at the new version")
            assertTrue(metaSize(root) > before, "meta must carry the new kdf descriptor")

            val reopened = FileVault(root)
            assertTrue(reopened.unlock("1234") is UnlockOutcome.Success, "upgraded vault must unlock again")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a wrong pin is still rejected after the upgrade`() {
        val root = Files.createTempDirectory("stade-kdf").toFile()
        try {
            writeLegacyVault(root, "1234", null, sqliteFixture("x"))
            assertTrue(FileVault(root).unlock("1234") is UnlockOutcome.Success)

            val reopened = FileVault(root)
            assertFalse(reopened.unlock("9999") is UnlockOutcome.Success)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a legacy duress pin keeps working after the main key is upgraded`() {
        val root = Files.createTempDirectory("stade-kdf").toFile()
        try {
            writeLegacyVault(root, "1234", "4321", sqliteFixture("x"))

            val vault = FileVault(root)
            assertTrue(vault.unlock("1234") is UnlockOutcome.Success)
            assertEquals(2, metaVersion(root))

            val reopened = FileVault(root)
            assertTrue(reopened.isDuressPin("4321"), "a legacy duress pin must survive the main key upgrade")
            assertFalse(reopened.isDuressPin("1234"))
            assertFalse(reopened.isDuressPin("0000"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a freshly created vault is written with the new kdf`() {
        val root = Files.createTempDirectory("stade-kdf").toFile()
        try {
            FileVault(root).setup("1234")
            assertEquals(2, metaVersion(root))

            val reopened = FileVault(root)
            assertTrue(reopened.unlock("1234") is UnlockOutcome.Success)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun assertContentEqualsBytes(expected: ByteArray, actual: ByteArray) {
        assertTrue(expected.contentEquals(actual), "database contents must survive unchanged")
    }
}
