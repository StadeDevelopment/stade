package dev.stade.db

import app.cash.sqldelight.db.SqlDriver

expect class DriverFactory {
    fun create(dbFilePath: String, key: String): SqlDriver
}

fun createDatabase(factory: DriverFactory, dbFilePath: String, key: String): StadeDb =
    StadeDb(factory.create(dbFilePath, key))

private val RAW_KEY_PATTERN = Regex("^[0-9a-f]{64}$")

fun sqlCipherRawKey(hexKey: String): String {
    require(RAW_KEY_PATTERN.matches(hexKey)) { "the database key must be 32 bytes of lowercase hex" }
    return "x'$hexKey'"
}
