package dev.stade.db

import app.cash.sqldelight.db.SqlDriver

expect class DriverFactory {
    fun create(dbFilePath: String, key: String): SqlDriver
}

fun createDatabase(factory: DriverFactory, dbFilePath: String, key: String): StadeDb =
    StadeDb(factory.create(dbFilePath, key))
