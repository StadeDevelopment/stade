package dev.stade.db

import java.io.File
import java.nio.file.Files
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val KEY = "6666666666666666666666666666666666666666666666666666666666666666"

class EncryptedQueryCostTest {

    @Test
    fun anEncryptedQueryCostsAboutAsMuchAsAPlainOne() {
        val dir = Files.createTempDirectory("query-cost").toFile()
        try {
            val driver = DriverFactory().create(File(dir, "stade.db").absolutePath, KEY)
            val db = StadeDb(driver)
            db.stadeDbQueries.putKv("k", "v".encodeToByteArray())
            repeat(5) { db.stadeDbQueries.getKv("k").executeAsOneOrNull() }

            val perQueryMs = measureNanoTime {
                repeat(40) { db.stadeDbQueries.getKv("k").executeAsOneOrNull() }
            } / 40 / 1_000_000.0
            driver.close()

            println("encrypted query: %.2f ms".format(perQueryMs))
            assertTrue(
                perQueryMs < 20.0,
                "each query opens a connection, so key derivation on open makes every screen " +
                    "change stall; it took $perQueryMs ms per query"
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theDatabaseKeyMustBeAFullRandomKey() {
        assertFailsWith<IllegalArgumentException> { sqlCipherRawKey("short") }
        assertFailsWith<IllegalArgumentException> { sqlCipherRawKey("G".repeat(64)) }
        assertTrue(sqlCipherRawKey(KEY) == "x'$KEY'")
    }
}
