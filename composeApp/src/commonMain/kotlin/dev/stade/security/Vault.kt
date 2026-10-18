package dev.stade.security

interface Vault {
    fun isInitialized(): Boolean
    fun isUnlocked(): Boolean
    fun setup(password: String)
    fun unlock(password: String): UnlockOutcome
    fun verifyPassword(candidate: String): UnlockOutcome
    fun tryAutoUnlock(): Boolean
    fun changePassword(currentPassword: String, newPassword: String): Boolean
    fun hasDuressPin(): Boolean
    fun setDuressPin(pin: String)
    fun isDuressPin(candidate: String): Boolean
    fun clearDuressPin()
    fun flushAndKeep()
    fun <T> withFlushLock(block: () -> T): T
    fun flushAndClose()
    fun wipe()
    fun metaPath(): String
    fun plaintextDbPath(): String
    fun encryptedDbPath(): String
    fun isScrambleKeypadEnabled(): Boolean
    fun setScrambleKeypadEnabled(enabled: Boolean)
    fun sessionTimeoutSeconds(): Int
    fun setSessionTimeoutSeconds(value: Int)
    fun isScreenshotBlockingEnabled(): Boolean
    fun setScreenshotBlockingEnabled(enabled: Boolean)
    fun failedAttempts(): Int
    fun lockoutUntilMillis(): Long
    fun nowMillis(): Long
}

sealed interface UnlockOutcome {
    data object Success : UnlockOutcome
    data object NotInitialized : UnlockOutcome
    data class Wrong(val remainingBeforeLockout: Int) : UnlockOutcome
    data class LockedOut(val untilMillis: Long) : UnlockOutcome
    data class Error(val message: String) : UnlockOutcome
}

object SessionTimeout {
    const val IMMEDIATE: Int = 0
    const val NEVER: Int = -1
    val OPTIONS: List<Int> = listOf(IMMEDIATE, 30, 60, 5 * 60, 15 * 60, 60 * 60, NEVER)
    fun label(value: Int): String = when (value) {
        IMMEDIATE -> "Hemen"
        NEVER -> "Asla"
        30 -> "30 saniye"
        60 -> "1 dakika"
        5 * 60 -> "5 dakika"
        15 * 60 -> "15 dakika"
        60 * 60 -> "1 saat"
        else -> if (value < 60) "$value saniye" else "${value / 60} dakika"
    }
}

