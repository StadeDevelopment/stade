package dev.stade.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSchemaUpgradeTest {

    private fun tempDbFile(): File =
        File.createTempFile("stade-schema-", ".db").also { it.delete() }

    private fun open(file: File): SqlDriver =
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", Properties())

    private fun userVersion(driver: SqlDriver): Long =
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor: SqlCursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            },
            parameters = 0
        ).value

    private fun hasColumn(driver: SqlDriver, table: String, column: String): Boolean =
        runCatching {
            driver.executeQuery(
                identifier = null,
                sql = "SELECT $column FROM $table LIMIT 0",
                mapper = { _: SqlCursor -> QueryResult.Value(Unit) },
                parameters = 0
            )
        }.isSuccess

    private fun seedContact(driver: SqlDriver, id: String) {
        driver.execute(
            null,
            "INSERT INTO LocalIdentity(id, nickname, publicKey, privateKey, handshakePublicKey, " +
                "handshakePrivateKey, mlkemPublicKey, mlkemPrivateKey, mldsaPublicKey, " +
                "mldsaPrivateKey, createdAt) VALUES " +
                "('owner', 'me', x'00', x'00', x'00', x'00', x'00', x'00', x'00', x'00', 1)",
            0
        )
        driver.execute(
            null,
            "INSERT INTO Contact(id, ownerId, nickname, publicKey, handshakePublicKey, " +
                "mlkemPublicKey, mldsaPublicKey, rootKey, createdAt) VALUES " +
                "('$id', 'owner', 'friend', x'00', x'00', x'00', x'00', x'00', 1)",
            0
        )
    }

    private fun contactCount(driver: SqlDriver): Long =
        driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM Contact",
            mapper = { cursor: SqlCursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            },
            parameters = 0
        ).value

    @Test
    fun `a fresh database is created and stamped at the current version`() {
        val file = tempDbFile()
        val driver = DriverFactory().create(file.absolutePath)
        try {
            assertTrue(hasColumn(driver, "Contact", "addresses"))
            assertEquals(StadeDb.Schema.version, userVersion(driver))
        } finally {
            driver.close()
            file.delete()
        }
    }

    @Test
    fun `a database predating the addresses column gains it without losing rows`() {
        val file = tempDbFile()
        val seed = open(file)
        try {
            StadeDb.Schema.create(seed)
            seedContact(seed, "c1")
            seed.execute(null, "ALTER TABLE Contact DROP COLUMN addresses", 0)
            seed.execute(null, "PRAGMA user_version = 0", 0)
            assertFalse(hasColumn(seed, "Contact", "addresses"))
        } finally {
            seed.close()
        }

        val driver = DriverFactory().create(file.absolutePath)
        try {
            assertTrue(
                hasColumn(driver, "Contact", "addresses"),
                "an upgraded database must gain the addresses column"
            )
            assertEquals(StadeDb.Schema.version, userVersion(driver))
            assertEquals(1L, contactCount(driver))
        } finally {
            driver.close()
            file.delete()
        }
    }

    @Test
    fun `a database with addresses but no version stamp does not re-run that migration`() {
        val file = tempDbFile()
        val seed = open(file)
        try {
            StadeDb.Schema.create(seed)
            seedContact(seed, "c1")
            seed.execute(null, "PRAGMA user_version = 0", 0)
            assertTrue(hasColumn(seed, "Contact", "addresses"))
        } finally {
            seed.close()
        }

        val driver = DriverFactory().create(file.absolutePath)
        try {
            assertTrue(hasColumn(driver, "Contact", "addresses"))
            assertEquals(
                StadeDb.Schema.version,
                userVersion(driver),
                "the true version must be inferred and stamped rather than migrating from zero"
            )
            assertEquals(1L, contactCount(driver))
        } finally {
            driver.close()
            file.delete()
        }
    }

    @Test
    fun `opening an already current database again changes nothing`() {
        val file = tempDbFile()
        val first = DriverFactory().create(file.absolutePath)
        try {
            seedContact(first, "c1")
            assertEquals(StadeDb.Schema.version, userVersion(first))
        } finally {
            first.close()
        }

        val second = DriverFactory().create(file.absolutePath)
        try {
            assertEquals(StadeDb.Schema.version, userVersion(second))
            assertEquals(1L, contactCount(second))
            assertTrue(hasColumn(second, "Contact", "addresses"))
        } finally {
            second.close()
            file.delete()
        }
    }
}
