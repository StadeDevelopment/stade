package dev.stade.security

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileVaultTest {

    @Test
    fun rewritesVaultMetaWithoutLeavingTemporaryFile() {
        val root = Files.createTempDirectory("stade-vault-test")
        try {
            val vault = FileVault(root.toFile())
            vault.setup("1234")
            vault.setScrambleKeypadEnabled(true)

            assertFalse(root.resolve("stade.vault.tmp").toFile().exists())

            val reopened = FileVault(root.toFile())
            assertTrue(reopened.unlock("1234") is UnlockOutcome.Success)
            assertTrue(reopened.isScrambleKeypadEnabled())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun flushAndCloseLocksTheVaultAndDropsTheKey() {
        val root = Files.createTempDirectory("stade-vault-test")
        try {
            val vault = FileVault(root.toFile())
            vault.setup("1234")
            assertTrue(vault.databaseKey() != null)

            vault.flushAndClose()

            assertFalse(vault.isUnlocked())
            assertEquals(null, vault.databaseKey(), "the database key must not outlive the lock")
        } finally {
            root.toFile().deleteRecursively()
        }
    }



    @Test
    fun verifyPasswordAcceptsTheRealPinWithoutTouchingTheSession() {
        val root = Files.createTempDirectory("stade-vault-test")
        try {
            val vault = FileVault(root.toFile())
            vault.setup("1234")
            val plaintext = root.resolve("stade.db").toFile()
            plaintext.writeText("SQLite format 3\u0000test data")
            val metaBefore = root.resolve("stade.vault").toFile().readBytes()

            assertTrue(vault.verifyPassword("1234") is UnlockOutcome.Success)

            assertTrue(vault.isUnlocked(), "a confirmation must not lock the vault")
            assertTrue(plaintext.exists(), "a confirmation must not disturb the open database")
            assertContentEquals(
                metaBefore,
                root.resolve("stade.vault").toFile().readBytes(),
                "a correct confirmation must not rewrite vault meta"
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun verifyPasswordRejectsAWrongPinAndCountsTheAttempt() {
        val root = Files.createTempDirectory("stade-vault-test")
        try {
            val vault = FileVault(root.toFile())
            vault.setup("1234")

            val outcome = vault.verifyPassword("9999")

            assertTrue(outcome is UnlockOutcome.Wrong, "a wrong PIN must be reported as wrong")
            assertEquals(1, vault.failedAttempts(), "a wrong confirmation must count toward lockout")
            assertTrue(vault.verifyPassword("1234") is UnlockOutcome.Success)
            assertEquals(0, vault.failedAttempts(), "a correct PIN must clear the counter")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun verifyPasswordStopsWhileTheVaultIsLockedOut() {
        val root = Files.createTempDirectory("stade-vault-test")
        try {
            val vault = FileVault(root.toFile())
            vault.setup("1234")
            repeat(4) { vault.verifyPassword("9999") }

            val outcome = vault.verifyPassword("1234")

            assertTrue(
                outcome is UnlockOutcome.LockedOut,
                "the confirmation prompt must respect the lockout the lock screen applies"
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

}
