package dev.stade.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.File

actual class DriverFactory(private val context: Context) {
    actual fun create(dbFilePath: String): SqlDriver {
        val name = File(dbFilePath).name
        val file = context.getDatabasePath(name)
        if (file.exists() && file.length() > 0L) {
            val stored = runCatching {
                SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    .use { it.version.toLong() }
            }.getOrDefault(0L)
            DatabaseSchema.requireNotDowngrade(stored)
        }
        return AndroidSqliteDriver(
            schema = StadeDb.Schema,
            context = context,
            name = name,
            callback = StoragePragmaCallback(StadeDb.Schema)
        )
    }
}

private class StoragePragmaCallback(
    schema: app.cash.sqldelight.db.SqlSchema<app.cash.sqldelight.db.QueryResult.Value<Unit>>
) : AndroidSqliteDriver.Callback(schema) {

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        runCatching { db.query("PRAGMA journal_mode = DELETE").use { it.moveToFirst() } }
        runCatching { db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() } }
    }
}
