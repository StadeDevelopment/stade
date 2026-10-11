package dev.stade.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
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
        return AndroidSqliteDriver(StadeDb.Schema, context, name)
    }
}
