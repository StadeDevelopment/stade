package dev.stade.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

actual class DriverFactory {
    actual fun create(dbFilePath: String): SqlDriver {
        val dbFile = File(dbFilePath)
        dbFile.parentFile?.mkdirs()
        val fresh = !dbFile.exists() || dbFile.length() == 0L
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", Properties())
        DesktopSchemaUpgrade.apply(driver, fresh)
        return driver
    }
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
        val current = if (stamped > 0L) stamped else inferVersion(driver)

        if (current >= target) {
            if (stamped <= 0L) writeVersion(driver, current)
            return
        }

        val migrated = runCatching {
            StadeDb.Schema.migrate(driver, current, target)
        }.isSuccess

        if (migrated) writeVersion(driver, target)
    }

    private fun inferVersion(driver: SqlDriver): Long =
        if (hasColumn(driver, "Contact", "addresses")) 2L else 1L

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
