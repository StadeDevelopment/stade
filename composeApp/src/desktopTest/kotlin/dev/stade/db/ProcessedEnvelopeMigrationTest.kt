package dev.stade.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessedEnvelopeMigrationTest {

    private fun JdbcSqliteDriver.exec(sql: String) = execute(null, sql, 0)

    private fun JdbcSqliteDriver.countRows(sql: String): Long =
        executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor: SqlCursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            parameters = 0
        ).value

    @Test
    fun theSchemaVersionAdvancedWithTheNewMigration() {
        assertEquals(4L, StadeDb.Schema.version, "a new migration must raise the schema version")
    }

    @Test
    fun scopingReplayProtectionKeepsExistingRowsAndAddsTheContactKey() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.exec(
                "CREATE TABLE ProcessedEnvelope (" +
                    "messageId TEXT NOT NULL PRIMARY KEY, " +
                    "contactId TEXT NOT NULL, " +
                    "processedAt INTEGER NOT NULL)"
            )
            driver.exec("CREATE INDEX idxProcessedEnvelope ON ProcessedEnvelope(processedAt)")
            driver.exec("INSERT INTO ProcessedEnvelope VALUES ('m1','alice',10)")
            driver.exec("INSERT INTO ProcessedEnvelope VALUES ('m2','bob',20)")

            StadeDb.Schema.migrate(driver, 3, 4)

            assertEquals(
                2L,
                driver.countRows("SELECT COUNT(*) FROM ProcessedEnvelope"),
                "existing replay-protection rows must survive the migration"
            )
            driver.exec("INSERT INTO ProcessedEnvelope VALUES ('m1','bob',30)")
            assertEquals(
                2L,
                driver.countRows("SELECT COUNT(*) FROM ProcessedEnvelope WHERE messageId = 'm1'"),
                "the same message id must now be storable once per contact"
            )
            assertTrue(
                driver.countRows(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idxProcessedEnvelope'"
                ) > 0L,
                "the pruning index must be rebuilt"
            )
            assertEquals(
                0L,
                driver.countRows(
                    "SELECT COUNT(*) FROM sqlite_master WHERE name='ProcessedEnvelopeUnscoped'"
                ),
                "the migration must not leave its working table behind"
            )
        } finally {
            driver.close()
        }
    }
}
