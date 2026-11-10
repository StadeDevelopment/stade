package dev.stade.db

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val KEY = "8888888888888888888888888888888888888888888888888888888888888888"

class PassphraseKeyConversionTest {

    private fun withDir(body: (File) -> Unit) {
        val dir = Files.createTempDirectory("rekey").toFile()
        try { body(dir) } finally { dir.deleteRecursively() }
    }

    @Test
    fun aDatabaseFromTheFirst26_1_0BuildStillOpensWithItsData() = withDir { dir ->
        val path = File(dir, "stade.db").absolutePath
        val passphraseDriver = app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(
            "jdbc:sqlite:$path?cipher=sqlcipher&key=$KEY"
        )
        StadeDb.Schema.create(passphraseDriver)
        StadeDb(passphraseDriver).stadeDbQueries.putKv("carried", "from the earlier build".encodeToByteArray())
        passphraseDriver.close()

        val driver = DriverFactory().create(path, KEY)
        val value = StadeDb(driver).stadeDbQueries.getKv("carried").executeAsOneOrNull()?.decodeToString()
        driver.close()

        assertEquals("from the earlier build", value, "a vault set up on the first 26.1.0 build must keep its data")
        assertFalse(
            runCatching {
                DriverManager.getConnection("jdbc:sqlite:$path?cipher=sqlcipher&key=$KEY").use { c ->
                    c.createStatement().use { st -> st.executeQuery("SELECT count(*) FROM sqlite_master").use { it.next() } }
                }
            }.getOrDefault(false),
            "after conversion the slow passphrase form must no longer be needed"
        )
    }

    @Test
    fun aDatabaseAlreadyOnTheRawKeyIsLeftAlone() = withDir { dir ->
        val path = File(dir, "stade.db").absolutePath
        DriverFactory().create(path, KEY).also { StadeDb(it).stadeDbQueries.putKv("k", "v".encodeToByteArray()) }.close()
        val before = File(path).readBytes()

        convertPassphraseKeyToRawKey(path, KEY)

        assertTrue(before.contentEquals(File(path).readBytes()), "a current database must not be rewritten")
    }

    @Test
    fun aDatabaseUnderSomeOtherKeyIsNotTouched() = withDir { dir ->
        val path = File(dir, "stade.db").absolutePath
        DriverFactory().create(path, "9".repeat(64)).close()
        val before = File(path).readBytes()

        convertPassphraseKeyToRawKey(path, KEY)

        assertTrue(before.contentEquals(File(path).readBytes()), "a database this key cannot open must be left as it is")
    }
}
