package dev.stade.db

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlCipherProbeTest {

    private fun withDir(body: (File) -> Unit) {
        val dir = Files.createTempDirectory("cipher-probe").toFile()
        try {
            body(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun write(path: String, key: String, value: String) {
        DriverManager.getConnection(encryptedJdbcUrl(path, key)).use { c ->
            c.createStatement().use { st ->
                st.execute("CREATE TABLE IF NOT EXISTS t(v TEXT)")
                st.execute("INSERT INTO t VALUES('$value')")
            }
        }
    }

    private fun readBack(path: String, key: String): String? =
        DriverManager.getConnection(encryptedJdbcUrl(path, key)).use { c ->
            c.createStatement().use { st ->
                st.executeQuery("SELECT v FROM t LIMIT 1").use { rs ->
                    if (rs.next()) rs.getString(1) else null
                }
            }
        }

    @Test
    fun theDatabaseFileIsNotReadableWithoutTheKey() = withDir { dir ->
        val db = File(dir, "stade.db")
        write(db.absolutePath, "correct-horse", "UNIQUE-CIPHER-MARKER")

        val raw = db.readBytes()
        assertFalse(
            raw.decodeToString().contains("UNIQUE-CIPHER-MARKER"),
            "the row must not be readable in the file on disk"
        )
        assertFalse(
            raw.copyOfRange(0, 16).decodeToString().startsWith("SQLite format 3"),
            "the file must not even announce itself as a plain database"
        )
        assertTrue(raw.isNotEmpty())
    }

    @Test
    fun theRightKeyOpensItAgain() = withDir { dir ->
        val db = File(dir, "stade.db")
        write(db.absolutePath, "correct-horse", "carried")

        assertEquals("carried", readBack(db.absolutePath, "correct-horse"))
    }

    @Test
    fun theWrongKeyIsRefused() = withDir { dir ->
        val db = File(dir, "stade.db")
        write(db.absolutePath, "correct-horse", "secret")

        val outcome = runCatching { readBack(db.absolutePath, "wrong-horse") }

        assertTrue(
            outcome.isFailure || outcome.getOrNull() == null,
            "a wrong key must not yield the contents"
        )
    }

    @Test
    fun aPlainDatabaseIsNotMistakenForAnEncryptedOne() = withDir { dir ->
        val db = File(dir, "plain.db")
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { c ->
            c.createStatement().use { it.execute("CREATE TABLE t(v TEXT)") }
        }

        val outcome = runCatching {
            DriverManager.getConnection(encryptedJdbcUrl(db.absolutePath, "any-key")).use { c ->
                c.createStatement().use { it.executeQuery("SELECT count(*) FROM t").close() }
            }
        }
        assertTrue(outcome.isFailure, "an existing plaintext database must not silently open as encrypted")
    }
}
