package dev.stade.security

import dev.stade.crypto.CryptoApi
import dev.stade.db.StadeDb

private const val TRANSPORTS_LOCK_KV_KEY = "security.transportsLockEnabled"

class SecretStore(
    private val db: StadeDb,
    private val crypto: CryptoApi,
    private val vault: Vault,
    private val onScreenshotSettingChanged: () -> Unit = {}
) {
    fun isLockEnabled(): Boolean = vault.isInitialized()

    fun isTransportsLockEnabled(): Boolean =
        runCatching { db.stadeDbQueries.getKv(TRANSPORTS_LOCK_KV_KEY).executeAsOneOrNull() }
            .getOrNull()?.decodeToString() == "1"

    fun setTransportsLockEnabled(enabled: Boolean) {
        db.stadeDbQueries.putKv(TRANSPORTS_LOCK_KV_KEY, (if (enabled) "1" else "0").encodeToByteArray())
    }

    fun verifyPassphrase(passphrase: String): Boolean =
        vault.verifyPassword(passphrase) is UnlockOutcome.Success

    fun changePassphrase(current: String, new: String): Boolean =
        vault.changePassword(current, new)

    fun verifyPin(pin: String): Boolean = verifyPassphrase(pin)
    fun changePin(current: String, new: String): Boolean = changePassphrase(current, new)

    fun isScrambleKeypadEnabled(): Boolean = vault.isScrambleKeypadEnabled()

    fun setScrambleKeypadEnabled(enabled: Boolean) =
        vault.setScrambleKeypadEnabled(enabled)

    fun sessionTimeoutSeconds(): Int = vault.sessionTimeoutSeconds()

    fun setSessionTimeoutSeconds(value: Int) = vault.setSessionTimeoutSeconds(value)

    fun isScreenshotBlockingEnabled(): Boolean = vault.isScreenshotBlockingEnabled()

    fun setScreenshotBlockingEnabled(enabled: Boolean) {
        vault.setScreenshotBlockingEnabled(enabled)
        onScreenshotSettingChanged()
    }

    fun failedAttempts(): Int = vault.failedAttempts()

    fun lockoutUntilMillis(): Long = vault.lockoutUntilMillis()
}
