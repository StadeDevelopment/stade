package dev.stade.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.File

actual class DriverFactory(private val context: Context) {
    actual fun create(dbFilePath: String, key: String): SqlDriver {
        val name = File(dbFilePath).name
        loadCipherLibrary()
        return AndroidSqliteDriver(
            schema = StadeDb.Schema,
            context = context,
            name = name,
            factory = SupportOpenHelperFactory(sqlCipherRawKey(key).encodeToByteArray()),
            callback = StoragePragmaCallback(StadeDb.Schema)
        )
    }
}

private class StoragePragmaCallback(
    schema: app.cash.sqldelight.db.SqlSchema<app.cash.sqldelight.db.QueryResult.Value<Unit>>
) : AndroidSqliteDriver.Callback(schema) {

    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw DatabaseSchemaException(downgrade = true)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        runCatching { db.query("PRAGMA journal_mode = DELETE").use { it.moveToFirst() } }
        runCatching { db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() } }
    }
}

private var cipherLoaded = false

private fun loadCipherLibrary() {
    synchronized(StadeDb.Schema) {
        if (cipherLoaded) return
        System.loadLibrary("sqlcipher")
        cipherLoaded = true
    }
}
