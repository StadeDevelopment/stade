package dev.stade.security

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class FileVault(private val rootDir: File) : Vault {

    private val metaFile: File = File(rootDir, "stade.vault")
    private val encryptedDb: File = File(rootDir, "stade.db.enc")
    private val plaintextDb: File = File(rootDir, "stade.db")
    private val sessionFile: File = File(rootDir, "stade.session")

    private val rng = SecureRandom()
    private val flushLock = Any()

    @Volatile private var unlocked: Boolean = false
    @Volatile private var dek: ByteArray? = null
    @Volatile private var cached: Meta? = null

    init {
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    private data class Meta(
        val salt: ByteArray,
        val iterations: Int,
        val verifierNonce: ByteArray,
        val verifierCipher: ByteArray,
        val dekNonce: ByteArray,
        val dekCipher: ByteArray,
        var failedAttempts: Int,
        var lockoutUntilMillis: Long,
        var scrambleKeypad: Boolean,
        var sessionTimeoutSeconds: Int,
        var screenshotBlocking: Boolean = true,
        var hasDuress: Boolean = false,
        var duressSalt: ByteArray = ByteArray(SALT_LEN),
        var duressIterations: Int = 0,
        var duressVerifierNonce: ByteArray = ByteArray(NONCE_LEN),
        var duressVerifierCipher: ByteArray = ByteArray(VERIFIER_CT_LEN),
        val kdfKind: Int = KDF_PBKDF2,
        val kdfMemoryKib: Int = 0,
        val kdfParallelism: Int = 0,
        var duressKdfKind: Int = KDF_PBKDF2,
        var duressKdfMemoryKib: Int = 0,
        var duressKdfParallelism: Int = 0
    )

    override fun isInitialized(): Boolean = metaFile.exists() && metaFile.length() >= MIN_META_SIZE_LEGACY

    override fun isUnlocked(): Boolean = unlocked

    override fun metaPath(): String = metaFile.absolutePath

    override fun plaintextDbPath(): String = plaintextDb.absolutePath

    override fun encryptedDbPath(): String = encryptedDb.absolutePath

    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun setup(password: String) {
        require(password.isNotEmpty()) { "password" }
        val salt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
        val iters = ARGON_TIME_COST
        val kek = deriveArgon2(password, salt, iters, ARGON_MEMORY_KIB, ARGON_PARALLELISM)
        val newDek = ByteArray(DEK_LEN).also { rng.nextBytes(it) }
        val verifierNonce = ByteArray(NONCE_LEN).also { rng.nextBytes(it) }
        val dekNonce = ByteArray(NONCE_LEN).also { rng.nextBytes(it) }
        val verifierCt = gcmEncrypt(kek, verifierNonce, VERIFIER_PLAIN)
        val dekCt = gcmEncrypt(kek, dekNonce, newDek)
        val meta = Meta(
            salt = salt,
            iterations = iters,
            verifierNonce = verifierNonce,
            verifierCipher = verifierCt,
            dekNonce = dekNonce,
            dekCipher = dekCt,
            failedAttempts = 0,
            lockoutUntilMillis = 0L,
            scrambleKeypad = false,
            sessionTimeoutSeconds = SessionTimeout.IMMEDIATE,
            kdfKind = KDF_ARGON2ID,
            kdfMemoryKib = ARGON_MEMORY_KIB,
            kdfParallelism = ARGON_PARALLELISM
        )
        writeMeta(meta)
        cached = meta
        dek = newDek
        unlocked = true
        if (plaintextDb.exists()) {
            encryptFile(plaintextDb, encryptedDb, newDek)
        } else {
            if (encryptedDb.exists()) runCatching { secureDelete(encryptedDb) }
        }
        zero(kek)
    }

    override fun unlock(password: String): UnlockOutcome {
        if (!isInitialized()) return UnlockOutcome.NotInitialized
        val meta = readMeta() ?: return UnlockOutcome.Error(dev.stade.ui.i18n.I18n.current.vaultMetaUnreadable)
        val now = nowMillis()
        if (meta.lockoutUntilMillis > now) {
            return UnlockOutcome.LockedOut(meta.lockoutUntilMillis)
        }
        val kek = try {
            deriveWith(password, meta.salt, meta.kdfKind, meta.iterations, meta.kdfMemoryKib, meta.kdfParallelism)
        } catch (t: Throwable) {
            return UnlockOutcome.Error(t.message ?: dev.stade.ui.i18n.I18n.current.vaultKeyDerivationFailed)
        }
        val verifierOk = runCatching {
            val pt = gcmDecrypt(kek, meta.verifierNonce, meta.verifierCipher)
            java.security.MessageDigest.isEqual(pt, VERIFIER_PLAIN)
        }.getOrDefault(false)
        if (!verifierOk) {
            zero(kek)
            meta.failedAttempts += 1
            applyLockoutFor(meta)
            writeMeta(meta)
            cached = meta
            val remaining = (LOCKOUT_THRESHOLD - meta.failedAttempts).coerceAtLeast(0)
            return if (meta.lockoutUntilMillis > nowMillis()) {
                UnlockOutcome.LockedOut(meta.lockoutUntilMillis)
            } else {
                UnlockOutcome.Wrong(remaining)
            }
        }
        val decryptedDek = runCatching {
            gcmDecrypt(kek, meta.dekNonce, meta.dekCipher)
        }.getOrElse {
            zero(kek)
            return UnlockOutcome.Error(it.message ?: dev.stade.ui.i18n.I18n.current.vaultDekDecryptFailed)
        }
        zero(kek)
        meta.failedAttempts = 0
        meta.lockoutUntilMillis = 0L
        val needsKdfUpgrade = meta.kdfKind != KDF_ARGON2ID ||
            meta.kdfMemoryKib != ARGON_MEMORY_KIB ||
            meta.iterations != ARGON_TIME_COST ||
            meta.kdfParallelism != ARGON_PARALLELISM
        val finalMeta: Meta = if (needsKdfUpgrade) {
            runCatching {
                val newSalt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
                val newKek = deriveArgon2(password, newSalt, ARGON_TIME_COST, ARGON_MEMORY_KIB, ARGON_PARALLELISM)
                val newVerifierNonce = ByteArray(NONCE_LEN).also { rng.nextBytes(it) }
                val newDekNonce = ByteArray(NONCE_LEN).also { rng.nextBytes(it) }
                val newVerifierCt = gcmEncrypt(newKek, newVerifierNonce, VERIFIER_PLAIN)
                val newDekCt = gcmEncrypt(newKek, newDekNonce, decryptedDek)
                zero(newKek)
                meta.copy(
                    salt = newSalt,
                    iterations = ARGON_TIME_COST,
                    verifierNonce = newVerifierNonce,
                    verifierCipher = newVerifierCt,
                    dekNonce = newDekNonce,
                    dekCipher = newDekCt,
                    kdfKind = KDF_ARGON2ID,
                    kdfMemoryKib = ARGON_MEMORY_KIB,
                    kdfParallelism = ARGON_PARALLELISM
                )
            }.getOrDefault(meta)
        } else meta
        writeMeta(finalMeta)
        cached = finalMeta
        if (plaintextDb.exists() && !isValidSqliteFile(plaintextDb)) {
            runCatching { secureDelete(plaintextDb) }
        }
        if (!plaintextDb.exists()) {
            if (encryptedDb.exists()) {
                val restored = runCatching {
                    decryptFile(encryptedDb, plaintextDb, decryptedDek)
                    isValidSqliteFile(plaintextDb)
                }.getOrDefault(false)
                if (!restored) {
                    runCatching { if (plaintextDb.exists()) secureDelete(plaintextDb) }
                    zero(decryptedDek)
                    return UnlockOutcome.Error(dev.stade.ui.i18n.I18n.current.vaultDatabaseDecryptFailed)
                }
            }
        }
        dek = decryptedDek
        unlocked = true
        syncSessionFile()
        return UnlockOutcome.Success
    }

    override fun tryAutoUnlock(): Boolean {
        if (unlocked) return true
        if (sessionFile.exists()) runCatching { secureDelete(sessionFile) }
        return false
    }

    override fun changePassword(currentPassword: String, newPassword: String): Boolean {
        if (!isInitialized()) return false
        val meta = readMeta() ?: return false
        val kek = deriveWith(
            currentPassword, meta.salt, meta.kdfKind, meta.iterations, meta.kdfMemoryKib, meta.kdfParallelism
        )
        val verifierOk = runCatching {
            val pt = gcmDecrypt(kek, meta.verifierNonce, meta.verifierCipher)
            java.security.MessageDigest.isEqual(pt, VERIFIER_PLAIN)
        }.getOrDefault(false)
        if (!verifierOk) {
            zero(kek)
            return false
        }
        val currentDek = runCatching {
            gcmDecrypt(kek, meta.dekNonce, meta.dekCipher)
        }.getOrElse {
            zero(kek)
            return false
        }
        zero(kek)
        val newSalt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
        val newKek = deriveArgon2(newPassword, newSalt, ARGON_TIME_COST, ARGON_MEMORY_KIB, ARGON_PARALLELISM)
        val newVerifierNonce = ByteArray(NONCE_LEN).also { rng.nextBytes(it) }
        val newDekNonce = ByteArray(NONCE_LEN).also { rng.nextBytes(it) }
        val newVerifierCt = gcmEncrypt(newKek, newVerifierNonce, VERIFIER_PLAIN)
        val newDekCt = gcmEncrypt(newKek, newDekNonce, currentDek)
        zero(newKek)
        val updated = meta.copy(
            salt = newSalt,
            iterations = ARGON_TIME_COST,
            kdfKind = KDF_ARGON2ID,
            kdfMemoryKib = ARGON_MEMORY_KIB,
            kdfParallelism = ARGON_PARALLELISM,
            verifierNonce = newVerifierNonce,
            verifierCipher = newVerifierCt,
            dekNonce = newDekNonce,
            dekCipher = newDekCt,
            failedAttempts = 0,
            lockoutUntilMillis = 0L
        )
        writeMeta(updated)
        cached = updated
        dek = currentDek
        unlocked = true
        return true
    }

    override fun hasDuressPin(): Boolean = (cached ?: readMeta())?.hasDuress ?: false

    override fun setDuressPin(pin: String) {
        require(pin.isNotEmpty()) { "pin" }
        val meta = readMeta() ?: return
        val salt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
        val kek = deriveArgon2(pin, salt, ARGON_TIME_COST, ARGON_MEMORY_KIB, ARGON_PARALLELISM)
        val nonce = ByteArray(NONCE_LEN).also { rng.nextBytes(it) }
        val ct = gcmEncrypt(kek, nonce, DURESS_VERIFIER_PLAIN)
        zero(kek)
        meta.hasDuress = true
        meta.duressSalt = salt
        meta.duressIterations = ARGON_TIME_COST
        meta.duressKdfKind = KDF_ARGON2ID
        meta.duressKdfMemoryKib = ARGON_MEMORY_KIB
        meta.duressKdfParallelism = ARGON_PARALLELISM
        meta.duressVerifierNonce = nonce
        meta.duressVerifierCipher = ct
        writeMeta(meta)
        cached = meta
    }

    override fun isDuressPin(candidate: String): Boolean {
        val meta = cached ?: readMeta() ?: return false
        if (!meta.hasDuress) return false
        val kek = try {
            deriveWith(
                candidate, meta.duressSalt, meta.duressKdfKind,
                meta.duressIterations, meta.duressKdfMemoryKib, meta.duressKdfParallelism
            )
        } catch (t: Throwable) {
            return false
        }
        val ok = runCatching {
            val pt = gcmDecrypt(kek, meta.duressVerifierNonce, meta.duressVerifierCipher)
            java.security.MessageDigest.isEqual(pt, DURESS_VERIFIER_PLAIN)
        }.getOrDefault(false)
        zero(kek)
        return ok
    }

    override fun clearDuressPin() {
        val meta = readMeta() ?: return
        meta.hasDuress = false
        meta.duressSalt = ByteArray(SALT_LEN)
        meta.duressIterations = 0
        meta.duressVerifierNonce = ByteArray(NONCE_LEN)
        meta.duressVerifierCipher = ByteArray(VERIFIER_CT_LEN)
        meta.duressKdfKind = KDF_PBKDF2
        meta.duressKdfMemoryKib = 0
        meta.duressKdfParallelism = 0
        writeMeta(meta)
        cached = meta
    }

    override fun <T> withFlushLock(block: () -> T): T = synchronized(flushLock) { block() }

    override fun flushAndKeep() {
        synchronized(flushLock) {
            val key = dek ?: return
            if (!plaintextDb.exists()) return
            encryptFile(plaintextDb, encryptedDb, key)
        }
    }

    override fun flushAndClose() {
        synchronized(flushLock) {
            val key = dek
            if (key != null && plaintextDb.exists()) {
                encryptFile(plaintextDb, encryptedDb, key)
            }
            if (plaintextDb.exists()) {
                secureDelete(plaintextDb)
            }
            val current = dek
            if (current != null) {
                zero(current)
                dek = null
            }
            unlocked = false
        }
    }

    override fun wipe() {
        val current = dek
        if (current != null) zero(current)
        dek = null
        unlocked = false
        cached = null
        runCatching { if (plaintextDb.exists()) secureDelete(plaintextDb) }
        runCatching { if (encryptedDb.exists()) secureDelete(encryptedDb) }
        runCatching { if (metaFile.exists()) secureDelete(metaFile) }
        runCatching { if (sessionFile.exists()) secureDelete(sessionFile) }
        runCatching { clearTorIdentity() }
    }

    override fun isScrambleKeypadEnabled(): Boolean = (cached ?: readMeta())?.scrambleKeypad ?: false

    override fun setScrambleKeypadEnabled(enabled: Boolean) {
        val meta = readMeta() ?: return
        meta.scrambleKeypad = enabled
        writeMeta(meta)
        cached = meta
    }

    override fun sessionTimeoutSeconds(): Int =
        (cached ?: readMeta())?.sessionTimeoutSeconds ?: SessionTimeout.IMMEDIATE

    override fun setSessionTimeoutSeconds(value: Int) {
        val meta = readMeta() ?: return
        meta.sessionTimeoutSeconds = value
        writeMeta(meta)
        cached = meta
        syncSessionFile()
    }

    override fun isScreenshotBlockingEnabled(): Boolean =
        (cached ?: readMeta())?.screenshotBlocking ?: true

    override fun setScreenshotBlockingEnabled(enabled: Boolean) {
        val meta = readMeta() ?: return
        meta.screenshotBlocking = enabled
        writeMeta(meta)
        cached = meta
    }

    override fun failedAttempts(): Int = (cached ?: readMeta())?.failedAttempts ?: 0

    override fun lockoutUntilMillis(): Long = (cached ?: readMeta())?.lockoutUntilMillis ?: 0L

    private fun applyLockoutFor(meta: Meta) {
        val attempts = meta.failedAttempts
        val delaySeconds: Long = when {
            attempts < 3 -> 0L
            attempts == 3 -> 5L
            attempts == 4 -> 15L
            attempts == 5 -> 30L
            attempts == 6 -> 60L
            attempts == 7 -> 5L * 60L
            attempts == 8 -> 15L * 60L
            attempts == 9 -> 60L * 60L
            else -> 24L * 60L * 60L
        }
        meta.lockoutUntilMillis = if (delaySeconds <= 0L) 0L else nowMillis() + delaySeconds * 1000L
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val encoded = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return encoded
    }

    private fun deriveArgon2(
        password: String,
        salt: ByteArray,
        iterations: Int,
        memoryKib: Int,
        parallelism: Int
    ): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKib)
            .withParallelism(parallelism)
            .build()
        val generator = Argon2BytesGenerator().apply { init(params) }
        val out = ByteArray(KEY_BITS / 8)
        generator.generateBytes(password.toCharArray(), out)
        return out
    }

    private fun deriveWith(
        password: String,
        salt: ByteArray,
        kind: Int,
        iterations: Int,
        memoryKib: Int,
        parallelism: Int
    ): ByteArray =
        if (kind == KDF_ARGON2ID) deriveArgon2(password, salt, iterations, memoryKib, parallelism)
        else deriveKey(password, salt, iterations)

    private fun gcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(plaintext)
    }

    private fun gcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(ciphertext)
    }

    private fun encryptFile(source: File, dest: File, key: ByteArray) {
        val plaintext = source.readBytes()
        val nonce = ByteArray(NONCE_LEN).also { rng.nextBytes(it) }
        val ct = gcmEncrypt(key, nonce, plaintext)
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        tmp.outputStream().use { os ->
            os.write(DB_MAGIC)
            os.write(byteArrayOf(DB_VERSION))
            os.write(nonce)
            os.write(ct)
        }
        if (tmp.renameTo(dest)) return
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    private fun decryptFile(source: File, dest: File, key: ByteArray) {
        val bytes = source.readBytes()
        require(bytes.size > DB_HEADER_LEN) { "encrypted db corrupt" }
        require(bytes.copyOfRange(0, 4).contentEquals(DB_MAGIC)) { "bad magic" }
        require(bytes[4] == DB_VERSION) { "bad version" }
        val nonce = bytes.copyOfRange(5, 5 + NONCE_LEN)
        val ct = bytes.copyOfRange(5 + NONCE_LEN, bytes.size)
        val pt = gcmDecrypt(key, nonce, ct)
        dest.writeBytes(pt)
    }

    private fun readMeta(): Meta? {
        if (!metaFile.exists()) return null
        val bytes = runCatching { metaFile.readBytes() }.getOrNull() ?: return null
        if (bytes.size < MIN_META_SIZE_LEGACY) return null
        val buf = ByteBuffer.wrap(bytes)
        val magic = ByteArray(4).also { buf.get(it) }
        if (!magic.contentEquals(META_MAGIC)) return null
        val version = buf.get()
        if (version != META_VERSION && version != META_VERSION_LEGACY) return null
        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val iterations = buf.int
        val verifierNonce = ByteArray(NONCE_LEN).also { buf.get(it) }
        val verifierCipher = ByteArray(VERIFIER_CT_LEN).also { buf.get(it) }
        val dekNonce = ByteArray(NONCE_LEN).also { buf.get(it) }
        val dekCipher = ByteArray(DEK_CT_LEN).also { buf.get(it) }
        val failed = buf.int
        val lockoutUntil = buf.long
        val scramble = buf.get() != 0.toByte()
        val timeout = buf.int
        val screenshot = if (buf.hasRemaining()) buf.get() != 0.toByte() else true
        val hasDuress: Boolean
        val duressSalt: ByteArray
        val duressIterations: Int
        val duressVerifierNonce: ByteArray
        val duressVerifierCipher: ByteArray
        if (buf.remaining() >= DURESS_SUFFIX_LEN) {
            hasDuress = buf.get() != 0.toByte()
            duressSalt = ByteArray(SALT_LEN).also { buf.get(it) }
            duressIterations = buf.int
            duressVerifierNonce = ByteArray(NONCE_LEN).also { buf.get(it) }
            duressVerifierCipher = ByteArray(VERIFIER_CT_LEN).also { buf.get(it) }
        } else {
            hasDuress = false
            duressSalt = ByteArray(SALT_LEN)
            duressIterations = 0
            duressVerifierNonce = ByteArray(NONCE_LEN)
            duressVerifierCipher = ByteArray(VERIFIER_CT_LEN)
        }
        val kdfKind: Int
        val kdfMemoryKib: Int
        val kdfParallelism: Int
        val duressKdfKind: Int
        val duressKdfMemoryKib: Int
        val duressKdfParallelism: Int
        if (buf.remaining() >= KDF_SUFFIX_LEN) {
            kdfKind = buf.get().toInt()
            kdfMemoryKib = buf.int
            kdfParallelism = buf.int
            duressKdfKind = buf.get().toInt()
            duressKdfMemoryKib = buf.int
            duressKdfParallelism = buf.int
        } else {
            kdfKind = KDF_PBKDF2
            kdfMemoryKib = 0
            kdfParallelism = 0
            duressKdfKind = KDF_PBKDF2
            duressKdfMemoryKib = 0
            duressKdfParallelism = 0
        }
        return Meta(
            salt = salt,
            iterations = iterations,
            verifierNonce = verifierNonce,
            verifierCipher = verifierCipher,
            dekNonce = dekNonce,
            dekCipher = dekCipher,
            failedAttempts = failed,
            lockoutUntilMillis = lockoutUntil,
            scrambleKeypad = scramble,
            sessionTimeoutSeconds = timeout,
            screenshotBlocking = screenshot,
            hasDuress = hasDuress,
            duressSalt = duressSalt,
            duressIterations = duressIterations,
            duressVerifierNonce = duressVerifierNonce,
            duressVerifierCipher = duressVerifierCipher,
            kdfKind = kdfKind,
            kdfMemoryKib = kdfMemoryKib,
            kdfParallelism = kdfParallelism,
            duressKdfKind = duressKdfKind,
            duressKdfMemoryKib = duressKdfMemoryKib,
            duressKdfParallelism = duressKdfParallelism
        )
    }

    @Synchronized
    private fun writeMeta(meta: Meta) {
        val out = ByteArray(FULL_META_SIZE)
        val buf = ByteBuffer.wrap(out)
        buf.put(META_MAGIC)
        buf.put(META_VERSION)
        buf.put(meta.salt)
        buf.putInt(meta.iterations)
        buf.put(meta.verifierNonce)
        buf.put(meta.verifierCipher)
        buf.put(meta.dekNonce)
        buf.put(meta.dekCipher)
        buf.putInt(meta.failedAttempts)
        buf.putLong(meta.lockoutUntilMillis)
        buf.put(if (meta.scrambleKeypad) 1.toByte() else 0.toByte())
        buf.putInt(meta.sessionTimeoutSeconds)
        buf.put(if (meta.screenshotBlocking) 1.toByte() else 0.toByte())
        buf.put(if (meta.hasDuress) 1.toByte() else 0.toByte())
        buf.put(meta.duressSalt)
        buf.putInt(meta.duressIterations)
        buf.put(meta.duressVerifierNonce)
        buf.put(meta.duressVerifierCipher)
        buf.put(meta.kdfKind.toByte())
        buf.putInt(meta.kdfMemoryKib)
        buf.putInt(meta.kdfParallelism)
        buf.put(meta.duressKdfKind.toByte())
        buf.putInt(meta.duressKdfMemoryKib)
        buf.putInt(meta.duressKdfParallelism)
        val tmp = File(metaFile.parentFile, metaFile.name + ".tmp")
        tmp.writeBytes(out)
        if (tmp.renameTo(metaFile)) return
        if (metaFile.exists()) metaFile.delete()
        if (!tmp.renameTo(metaFile)) {
            tmp.copyTo(metaFile, overwrite = true)
            tmp.delete()
        }
    }

    private fun zero(b: ByteArray) {
        for (i in b.indices) b[i] = 0
    }

    private fun syncSessionFile() {
        if (sessionFile.exists()) runCatching { secureDelete(sessionFile) }
    }

    private fun isValidSqliteFile(f: File): Boolean {
        if (f.length() < 16L) return false
        return try {
            val header = ByteArray(16)
            f.inputStream().use { it.read(header) }
            header[0] == 0x53.toByte() && header[1] == 0x51.toByte() &&
            header[2] == 0x4C.toByte() && header[3] == 0x69.toByte() &&
            header[4] == 0x74.toByte() && header[5] == 0x65.toByte()
        } catch (_: Throwable) {
            false
        }
    }

    private fun secureDelete(f: File) {
        if (!f.exists()) return
        var overwriteFailure: Throwable? = null
        try {
            if (f.length() > 0) {
                RandomAccessFile(f, "rw").use { raf ->
                    val len = raf.length()
                    val zeros = ByteArray(4096)
                    var remaining = len
                    raf.seek(0)
                    while (remaining > 0) {
                        val n = minOf(remaining, zeros.size.toLong()).toInt()
                        raf.write(zeros, 0, n)
                        remaining -= n
                    }
                    raf.fd.sync()
                }
            }
        } catch (error: Throwable) {
            overwriteFailure = error
        }
        if (!f.delete() && f.exists()) {
            throw java.io.IOException("Unable to remove ${f.absolutePath}", overwriteFailure)
        }
    }

    companion object {
        private val META_MAGIC = byteArrayOf('S'.code.toByte(), 'T'.code.toByte(), 'D'.code.toByte(), 'V'.code.toByte())
        private const val META_VERSION: Byte = 0x02
        private const val META_VERSION_LEGACY: Byte = 0x01
        private val DB_MAGIC = byteArrayOf('S'.code.toByte(), 'T'.code.toByte(), 'D'.code.toByte(), 'D'.code.toByte())
        private const val DB_VERSION: Byte = 0x01
        private const val DB_HEADER_LEN: Int = 4 + 1 + 12
        private const val SALT_LEN = 16
        private const val NONCE_LEN = 12
        private const val DEK_LEN = 32
        private const val DEK_CT_LEN = DEK_LEN + 16
        private const val VERIFIER_CT_LEN = 16 + 16
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val KDF_ITERS = 210_000
        private const val KDF_PBKDF2 = 0
        private const val KDF_ARGON2ID = 1
        private const val ARGON_MEMORY_KIB = 19_456
        private const val ARGON_TIME_COST = 2
        private const val ARGON_PARALLELISM = 1
        private const val KDF_SUFFIX_LEN = (1 + 4 + 4) * 2
        private const val LOCKOUT_THRESHOLD = 5
        private const val DURESS_SUFFIX_LEN = 1 + SALT_LEN + 4 + NONCE_LEN + VERIFIER_CT_LEN
        private const val MIN_META_SIZE_PRE_DURESS =
            4 + 1 + SALT_LEN + 4 + NONCE_LEN + VERIFIER_CT_LEN + NONCE_LEN + DEK_CT_LEN + 4 + 8 + 1 + 4 + 1
        private const val MIN_META_SIZE = MIN_META_SIZE_PRE_DURESS + DURESS_SUFFIX_LEN
        private const val FULL_META_SIZE = MIN_META_SIZE + KDF_SUFFIX_LEN
        private const val MIN_META_SIZE_LEGACY = MIN_META_SIZE_PRE_DURESS - 1
        private val VERIFIER_PLAIN = byteArrayOf(
            'S'.code.toByte(), 'T'.code.toByte(), 'A'.code.toByte(), 'D'.code.toByte(),
            'E'.code.toByte(), '-'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'Y'.code.toByte(),
            '-'.code.toByte(), '0'.code.toByte(), '1'.code.toByte(), 0
        )
        private val DURESS_VERIFIER_PLAIN = byteArrayOf(
            'S'.code.toByte(), 'T'.code.toByte(), 'A'.code.toByte(), 'D'.code.toByte(),
            'E'.code.toByte(), '-'.code.toByte(), 'D'.code.toByte(), 'U'.code.toByte(),
            'R'.code.toByte(), 'E'.code.toByte(), 'S'.code.toByte(), 'S'.code.toByte(),
            '-'.code.toByte(), '0'.code.toByte(), '1'.code.toByte(), 0
        )
    }
}

