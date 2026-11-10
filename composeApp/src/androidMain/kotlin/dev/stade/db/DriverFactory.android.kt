package dev.stade.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.File

actual class DriverFactory(private val context: Context) {
    actual fun create(dbFilePath: String, key: String): SqlDriver {
        val name = File(dbFilePath).name
        loadCipherLibrary()
        runCatching { convertPassphraseKeyToRawKey(context.getDatabasePath(name), key) }
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

private fun opensWith(file: File, password: ByteArray): Boolean = runCatching {
    SQLiteDatabase.openDatabase(file.absolutePath, password, null, SQLiteDatabase.OPEN_READWRITE, null).use { db ->
        db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
    }
}.getOrDefault(false)

private fun convertPassphraseKeyToRawKey(file: File, key: String) {
    if (!file.exists() || file.length() == 0L) return
    val rawKey = sqlCipherRawKey(key).encodeToByteArray()
    if (opensWith(file, rawKey)) return
    val passphrase = key.encodeToByteArray()
    if (!opensWith(file, passphrase)) return
    SQLiteDatabase.openDatabase(file.absolutePath, passphrase, null, SQLiteDatabase.OPEN_READWRITE, null).use { db ->
        db.changePassword(rawKey)
    }
}
