package dev.stade.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver

class DatabaseSchemaException(
    val downgrade: Boolean = false
) : IllegalStateException(
    if (downgrade) {
        "The local database was written by a newer version of Stade."
    } else {
        "The local database needs a compatible update before it can be opened."
    }
)

internal object DatabaseSchema {
    fun requireCompatible(driver: SqlDriver) {
        val compatible = runCatching {
            driver.executeQuery(
                identifier = null,
                sql = "SELECT mlkemPublicKey, mldsaPublicKey FROM Contact LIMIT 0",
                mapper = { _: SqlCursor -> QueryResult.Value(Unit) },
                parameters = 0
            )
        }.isSuccess
        if (!compatible) throw DatabaseSchemaException()
    }

    fun requireNotDowngrade(storedVersion: Long) {
        if (storedVersion > StadeDb.Schema.version) throw DatabaseSchemaException(downgrade = true)
    }
}
