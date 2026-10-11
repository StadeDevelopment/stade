package dev.stade.backup

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupSource(
    val name: String,
    val length: Long,
    val open: () -> InputStream
)

class BackupEntry(val name: String, val length: Long, val offset: Long)

sealed interface BackupReadOutcome {
    class Ok(val payload: File, val entries: List<BackupEntry>) : BackupReadOutcome
    data object WrongPassphrase : BackupReadOutcome
    data object Damaged : BackupReadOutcome
    data object NotABackup : BackupReadOutcome
    data object NewerVersion : BackupReadOutcome
}

sealed interface BackupVerifyOutcome {
    class Ok(val entries: List<BackupEntry>, val payloadBytes: Long) : BackupVerifyOutcome
    data object WrongPassphrase : BackupVerifyOutcome
    data object Damaged : BackupVerifyOutcome
    data object NotABackup : BackupVerifyOutcome
    data object NewerVersion : BackupVerifyOutcome
}

object BackupArchive {

    private val MAGIC = byteArrayOf(
        'S'.code.toByte(), 'T'.code.toByte(), 'D'.code.toByte(),
        'B'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte()
    )
    private const val VERSION_PBKDF2: Byte = 1
    private const val VERSION_ARGON2: Byte = 2
    private const val VERSION_CURRENT: Byte = VERSION_ARGON2
    private const val SALT_LEN = 16
    private const val PREFIX_LEN = 8
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val TAG_LEN = TAG_BITS / 8
    private const val LEAD_LEN = 6 + 1
    private const val HEADER_LEN_V1 = LEAD_LEN + SALT_LEN + 4 + PREFIX_LEN + 8 + 4
    private const val HEADER_LEN_V2 = HEADER_LEN_V1 + 4 + 4

    const val KDF_ITERS = 310_000
    const val ARGON_MEMORY_KIB = 65_536
    const val ARGON_TIME_COST = 3
    const val ARGON_PARALLELISM = 1
    const val CHUNK_SIZE = 1 shl 20
    const val MAX_PAYLOAD_BYTES = 4L * 1024 * 1024 * 1024
    const val MAX_NAME_LEN = 64

    private val rng = SecureRandom()

    fun payloadLengthOf(sources: List<BackupSource>): Long =
        sources.sumOf { 2L + it.name.encodeToByteArray().size + 8L + it.length }

    fun write(out: OutputStream, passphrase: CharArray, sources: List<BackupSource>) {
        val payloadLength = payloadLengthOf(sources)
        require(payloadLength <= MAX_PAYLOAD_BYTES) { "backup too large" }

        val salt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
        val prefix = ByteArray(PREFIX_LEN).also { rng.nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER_LEN_V2).apply {
            put(MAGIC)
            put(VERSION_CURRENT)
            put(salt)
            putInt(ARGON_TIME_COST)
            putInt(ARGON_MEMORY_KIB)
            putInt(ARGON_PARALLELISM)
            put(prefix)
            putLong(payloadLength)
            putInt(CHUNK_SIZE)
        }.array()
        out.write(header)

        val key = deriveArgon2(passphrase, salt, ARGON_TIME_COST, ARGON_MEMORY_KIB, ARGON_PARALLELISM)
        try {
            val payload = PayloadReader(sources)
            val buffer = ByteArray(CHUNK_SIZE)
            var remaining = payloadLength
            var index = 0
            while (remaining > 0) {
                val want = minOf(remaining, CHUNK_SIZE.toLong()).toInt()
                payload.readFully(buffer, want)
                val sealed = seal(key, prefix, index, header, buffer, want)
                out.write(sealed)
                remaining -= want
                index++
            }
            payload.close()
        } finally {
            key.fill(0)
        }
        out.flush()
    }

    private class ParsedHeader(
        val raw: ByteArray,
        val salt: ByteArray,
        val prefix: ByteArray,
        val payloadLength: Long,
        val chunkSize: Int,
        val version: Byte,
        val iterations: Int,
        val memoryKib: Int,
        val parallelism: Int
    )

    private sealed interface HeaderResult {
        class Ok(val header: ParsedHeader) : HeaderResult
        data object NotABackup : HeaderResult
        data object NewerVersion : HeaderResult
    }

    private fun readHeader(input: InputStream): HeaderResult {
        val lead = ByteArray(LEAD_LEN)
        if (!readFully(input, lead, LEAD_LEN)) return HeaderResult.NotABackup
        if (!lead.copyOfRange(0, 6).contentEquals(MAGIC)) return HeaderResult.NotABackup
        val version = lead[6]
        val totalLen = when (version) {
            VERSION_PBKDF2 -> HEADER_LEN_V1
            VERSION_ARGON2 -> HEADER_LEN_V2
            else -> return if (version > VERSION_CURRENT) HeaderResult.NewerVersion else HeaderResult.NotABackup
        }
        val rest = ByteArray(totalLen - LEAD_LEN)
        if (!readFully(input, rest, rest.size)) return HeaderResult.NotABackup
        val raw = lead + rest
        val buf = ByteBuffer.wrap(rest)
        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val iterations = buf.int
        val memoryKib = if (version == VERSION_ARGON2) buf.int else 0
        val parallelism = if (version == VERSION_ARGON2) buf.int else 0
        val prefix = ByteArray(PREFIX_LEN).also { buf.get(it) }
        val payloadLength = buf.long
        val chunkSize = buf.int
        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES) return HeaderResult.NotABackup
        if (chunkSize !in 1..(1 shl 26)) return HeaderResult.NotABackup
        if (version == VERSION_PBKDF2) {
            if (iterations !in 1..2_000_000) return HeaderResult.NotABackup
        } else {
            if (iterations !in 1..16) return HeaderResult.NotABackup
            if (memoryKib !in 8..(1 shl 21)) return HeaderResult.NotABackup
            if (parallelism !in 1..16) return HeaderResult.NotABackup
        }
        return HeaderResult.Ok(
            ParsedHeader(raw, salt, prefix, payloadLength, chunkSize, version, iterations, memoryKib, parallelism)
        )
    }

    private fun deriveFor(parsed: ParsedHeader, passphrase: CharArray): ByteArray =
        if (parsed.version == VERSION_ARGON2) {
            deriveArgon2(passphrase, parsed.salt, parsed.iterations, parsed.memoryKib, parsed.parallelism)
        } else {
            deriveKey(passphrase, parsed.salt, parsed.iterations)
        }

    fun read(input: InputStream, passphrase: CharArray, scratch: File): BackupReadOutcome {
        val parsed = when (val h = readHeader(input)) {
            is HeaderResult.Ok -> h.header
            HeaderResult.NewerVersion -> return BackupReadOutcome.NewerVersion
            HeaderResult.NotABackup -> return BackupReadOutcome.NotABackup
        }
        val header = parsed.raw
        val prefix = parsed.prefix
        val payloadLength = parsed.payloadLength
        val chunkSize = parsed.chunkSize

        val key = deriveFor(parsed, passphrase)
        val cipherBuf = ByteArray(chunkSize + TAG_LEN)
        var authFailed = false
        try {
            scratch.outputStream().use { sink ->
                var remaining = payloadLength
                var index = 0
                while (remaining > 0) {
                    val want = minOf(remaining, chunkSize.toLong()).toInt()
                    val sealedLen = want + TAG_LEN
                    if (!readFully(input, cipherBuf, sealedLen)) return BackupReadOutcome.Damaged
                    val plain = try {
                        open(key, prefix, index, header, cipherBuf, sealedLen)
                    } catch (_: AEADBadTagException) {
                        authFailed = true
                        return@use
                    }
                    sink.write(plain)
                    remaining -= want
                    index++
                }
            }
        } catch (_: Exception) {
            return BackupReadOutcome.Damaged
        } finally {
            key.fill(0)
        }
        if (authFailed) {
            runCatching { scratch.delete() }
            return BackupReadOutcome.WrongPassphrase
        }

        val entries = runCatching { indexEntries(scratch, payloadLength) }.getOrNull()
            ?: run {
                runCatching { scratch.delete() }
                return BackupReadOutcome.Damaged
            }
        return BackupReadOutcome.Ok(scratch, entries)
    }

    fun verify(input: InputStream, passphrase: CharArray): BackupVerifyOutcome {
        val parsed = when (val h = readHeader(input)) {
            is HeaderResult.Ok -> h.header
            HeaderResult.NewerVersion -> return BackupVerifyOutcome.NewerVersion
            HeaderResult.NotABackup -> return BackupVerifyOutcome.NotABackup
        }
        val key = deriveFor(parsed, passphrase)
        val cipherBuf = ByteArray(parsed.chunkSize + TAG_LEN)
        val scanner = EntryScanner()
        try {
            var remaining = parsed.payloadLength
            var index = 0
            while (remaining > 0) {
                val want = minOf(remaining, parsed.chunkSize.toLong()).toInt()
                if (!readFully(input, cipherBuf, want + TAG_LEN)) return BackupVerifyOutcome.Damaged
                val plain = try {
                    open(key, parsed.prefix, index, parsed.raw, cipherBuf, want + TAG_LEN)
                } catch (_: AEADBadTagException) {
                    return BackupVerifyOutcome.WrongPassphrase
                }
                scanner.feed(plain, plain.size)
                remaining -= want
                index++
            }
        } catch (_: Exception) {
            return BackupVerifyOutcome.Damaged
        } finally {
            key.fill(0)
        }
        val entries = runCatching { scanner.finish(parsed.payloadLength) }.getOrNull()
            ?: return BackupVerifyOutcome.Damaged
        return BackupVerifyOutcome.Ok(entries, parsed.payloadLength)
    }

    private class EntryScanner {
        private val entries = mutableListOf<BackupEntry>()
        private var state = STATE_NAME_LEN
        private var need = 2
        private val acc = ByteArray(MAX_NAME_LEN + 8)
        private var accLen = 0
        private var pendingName = ""
        private var dataRemaining = 0L
        private var consumed = 0L

        fun feed(buf: ByteArray, len: Int) {
            var pos = 0
            while (pos < len) {
                if (state == STATE_DATA) {
                    val take = minOf((len - pos).toLong(), dataRemaining).toInt()
                    pos += take
                    consumed += take
                    dataRemaining -= take
                    if (dataRemaining == 0L) {
                        state = STATE_NAME_LEN
                        need = 2
                        accLen = 0
                    }
                    continue
                }
                val take = minOf(len - pos, need - accLen)
                buf.copyInto(acc, accLen, pos, pos + take)
                accLen += take
                pos += take
                consumed += take
                if (accLen < need) continue
                when (state) {
                    STATE_NAME_LEN -> {
                        val nameLen = ((acc[0].toInt() and 0xff) shl 8) or (acc[1].toInt() and 0xff)
                        require(nameLen in 1..MAX_NAME_LEN) { "bad entry name" }
                        state = STATE_NAME
                        need = nameLen
                        accLen = 0
                    }
                    STATE_NAME -> {
                        pendingName = acc.copyOfRange(0, need).decodeToString()
                        state = STATE_LENGTH
                        need = 8
                        accLen = 0
                    }
                    STATE_LENGTH -> {
                        var length = 0L
                        for (i in 0 until 8) length = (length shl 8) or (acc[i].toLong() and 0xff)
                        require(length >= 0) { "bad entry length" }
                        entries.add(BackupEntry(pendingName, length, consumed))
                        dataRemaining = length
                        state = if (length == 0L) STATE_NAME_LEN else STATE_DATA
                        need = 2
                        accLen = 0
                    }
                }
            }
        }

        fun finish(payloadLength: Long): List<BackupEntry> {
            require(consumed == payloadLength) { "payload length mismatch" }
            require(state == STATE_NAME_LEN && accLen == 0) { "truncated entry" }
            return entries
        }

        private companion object {
            const val STATE_NAME_LEN = 0
            const val STATE_NAME = 1
            const val STATE_LENGTH = 2
            const val STATE_DATA = 3
        }
    }

    private fun indexEntries(payload: File, payloadLength: Long): List<BackupEntry> {
        val entries = mutableListOf<BackupEntry>()
        DataInputStream(payload.inputStream().buffered()).use { stream ->
            var offset = 0L
            while (offset < payloadLength) {
                val nameLen = stream.readUnsignedShort()
                require(nameLen in 1..MAX_NAME_LEN) { "bad entry name" }
                val nameBytes = ByteArray(nameLen)
                stream.readFully(nameBytes)
                val length = stream.readLong()
                require(length >= 0 && length <= payloadLength) { "bad entry length" }
                offset += 2L + nameLen + 8L
                entries.add(BackupEntry(nameBytes.decodeToString(), length, offset))
                var skipped = 0L
                while (skipped < length) {
                    val n = stream.skip(length - skipped)
                    if (n <= 0) throw EOFException("truncated entry")
                    skipped += n
                }
                offset += length
            }
            require(offset == payloadLength) { "payload length mismatch" }
        }
        return entries
    }

    private class PayloadReader(sources: List<BackupSource>) {
        private val queue = ArrayDeque(sources)
        private var current: InputStream? = null
        private var pending: ByteArray = ByteArray(0)
        private var pendingPos = 0
        private var currentRemaining = 0L

        fun readFully(target: ByteArray, count: Int) {
            var written = 0
            while (written < count) {
                if (pendingPos < pending.size) {
                    val n = minOf(count - written, pending.size - pendingPos)
                    pending.copyInto(target, written, pendingPos, pendingPos + n)
                    pendingPos += n
                    written += n
                    continue
                }
                val stream = current
                if (stream != null && currentRemaining > 0) {
                    val n = stream.read(target, written, minOf(count - written, currentRemaining.toInt()))
                    if (n <= 0) throw EOFException("backup source ended early")
                    currentRemaining -= n
                    written += n
                    continue
                }
                if (stream != null) {
                    stream.close()
                    current = null
                }
                val next = queue.removeFirstOrNull() ?: throw EOFException("backup sources exhausted")
                val nameBytes = next.name.encodeToByteArray()
                pending = ByteBuffer.allocate(2 + nameBytes.size + 8).apply {
                    putShort(nameBytes.size.toShort())
                    put(nameBytes)
                    putLong(next.length)
                }.array()
                pendingPos = 0
                current = next.open()
                currentRemaining = next.length
            }
        }

        fun close() {
            runCatching { current?.close() }
            current = null
        }
    }

    private fun seal(
        key: ByteArray,
        prefix: ByteArray,
        index: Int,
        header: ByteArray,
        plaintext: ByteArray,
        length: Int
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce(prefix, index))
        )
        cipher.updateAAD(aad(header, index))
        return cipher.doFinal(plaintext, 0, length)
    }

    private fun open(
        key: ByteArray,
        prefix: ByteArray,
        index: Int,
        header: ByteArray,
        ciphertext: ByteArray,
        length: Int
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce(prefix, index))
        )
        cipher.updateAAD(aad(header, index))
        return cipher.doFinal(ciphertext, 0, length)
    }

    private fun nonce(prefix: ByteArray, index: Int): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(index).array()

    private fun aad(header: ByteArray, index: Int): ByteArray =
        ByteBuffer.allocate(header.size + 4).put(header).putInt(index).array()

    private fun deriveArgon2(
        passphrase: CharArray,
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
        generator.generateBytes(passphrase, out)
        return out
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val encoded = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return encoded
    }

    private fun readFully(input: InputStream, target: ByteArray, count: Int): Boolean {
        var read = 0
        while (read < count) {
            val n = input.read(target, read, count - read)
            if (n < 0) return false
            read += n
        }
        return true
    }
}
