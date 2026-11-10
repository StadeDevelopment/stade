package dev.stade.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.sql.DriverManager
import java.util.Properties

actual class DriverFactory {
    actual fun create(dbFilePath: String, key: String): SqlDriver {
        val dbFile = File(dbFilePath)
        dbFile.parentFile?.mkdirs()
        val fresh = !dbFile.exists() || dbFile.length() == 0L
        if (!fresh) convertPassphraseKeyToRawKey(dbFile.absolutePath, key)
        val driver = JdbcSqliteDriver(encryptedJdbcUrl(dbFile.absolutePath, key), storageProperties())
        DesktopSchemaUpgrade.apply(driver, fresh)
        return driver
    }
}

fun encryptedJdbcUrl(dbFilePath: String, key: String): String =
    "jdbc:sqlite:$dbFilePath?cipher=sqlcipher&key=${sqlCipherRawKey(key)}"

private fun opensWith(url: String): Boolean = runCatching {
    DriverManager.getConnection(url).use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT count(*) FROM sqlite_master").use { it.next() }
        }
    }
}.getOrDefault(false)

internal fun convertPassphraseKeyToRawKey(dbFilePath: String, key: String) {
    if (opensWith(encryptedJdbcUrl(dbFilePath, key))) return
    val passphraseUrl = "jdbc:sqlite:$dbFilePath?cipher=sqlcipher&key=$key"
    if (!opensWith(passphraseUrl)) return
    DriverManager.getConnection(passphraseUrl).use { connection ->
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA rekey = \"${sqlCipherRawKey(key)}\"")
        }
    }
}

private fun storageProperties(): Properties = Properties().apply {
    setProperty("journal_mode", "DELETE")
    setProperty("secure_delete", "true")
}

internal object DesktopSchemaUpgrade {

    fun apply(driver: SqlDriver, fresh: Boolean) {
        val target = StadeDb.Schema.version

        if (fresh) {
            StadeDb.Schema.create(driver)
            writeVersion(driver, target)
            return
        }

        val stamped = readVersion(driver)
        DatabaseSchema.requireNotDowngrade(stamped)
        val current = if (stamped > 0L) stamped else inferVersion(driver)

        if (current >= target) {
            if (stamped <= 0L) writeVersion(driver, current)
            return
        }

        val migrated = runCatching {
            StadeDb.Schema.migrate(driver, current, target)
        }.isSuccess

        if (!migrated) throw DatabaseSchemaException()
        writeVersion(driver, target)
    }

    private fun inferVersion(driver: SqlDriver): Long = when {
        hasColumn(driver, "StadeGroup", "membershipEpoch") -> 5L
        hasColumn(driver, "Contact", "bio") -> 3L
        hasColumn(driver, "Contact", "addresses") -> 2L
        else -> 1L
    }

    private fun hasColumn(driver: SqlDriver, table: String, column: String): Boolean =
        runCatching {
            driver.executeQuery(
                identifier = null,
                sql = "SELECT $column FROM $table LIMIT 0",
                mapper = { _: SqlCursor -> QueryResult.Value(Unit) },
                parameters = 0
            )
        }.isSuccess

    private fun readVersion(driver: SqlDriver): Long =
        runCatching {
            driver.executeQuery(
                identifier = null,
                sql = "PRAGMA user_version",
                mapper = { cursor: SqlCursor ->
                    QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
                },
                parameters = 0
            ).value
        }.getOrDefault(0L)

    private fun writeVersion(driver: SqlDriver, version: Long) {
        runCatching { driver.execute(null, "PRAGMA user_version = $version", 0) }
    }
}
