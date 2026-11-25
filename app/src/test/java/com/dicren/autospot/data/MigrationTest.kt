package com.dicren.autospot.data

import android.content.Context
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val databaseFile =
        File(
            ApplicationProvider.getApplicationContext<Context>().cacheDir,
            "migration-${UUID.randomUUID()}.db",
        )

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            databaseFile,
            AndroidSQLiteDriver(),
            AppDatabase::class,
        )

    private fun SQLiteConnection.scalarLong(sql: String): Long =
        prepare(sql).use { statement ->
            assertTrue(statement.step())
            statement.getLong(0)
        }

    /** Inserts a v3 database with duplicate Bluetooth addresses and their history. */
    private suspend fun createV3WithDuplicates(): SQLiteConnection {
        val db = helper.createDatabase(3)
        db.executeSQL(
            """
            INSERT INTO vehicles (id, name, bluetoothAddress, bluetoothName, isActive) VALUES
                (1, 'First',  'AA:BB:CC:DD:EE:01', 'Car', 0),
                (2, 'Second', 'AA:BB:CC:DD:EE:01', 'Car', 1),
                (3, 'Other',  'AA:BB:CC:DD:EE:02', 'Other', 1)
            """.trimIndent(),
        )
        db.executeSQL(
            """
            INSERT INTO parking_locations (vehicleId, latitude, longitude, timestamp) VALUES
                (1, 1.0, 1.0, 100),
                (2, 2.0, 2.0, 200)
            """.trimIndent(),
        )
        return db
    }

    @Test
    fun `v3 to v4 merges duplicate vehicles and keeps their history`() =
        runTest {
            val v3 = createV3WithDuplicates()
            v3.close()

            val migrated = helper.runMigrationsAndValidate(4, listOf(MIGRATION_3_4))

            assertEquals(2L, migrated.scalarLong("SELECT COUNT(*) FROM vehicles"))
            assertEquals(2L, migrated.scalarLong("SELECT COUNT(*) FROM parking_locations"))

            // Both locations now belong to the surviving row (lowest id).
            assertEquals(
                2L,
                migrated.scalarLong("SELECT COUNT(*) FROM parking_locations WHERE vehicleId = 1"),
            )

            // Only one vehicle remains active after the migration.
            assertEquals(1L, migrated.scalarLong("SELECT COUNT(*) FROM vehicles WHERE isActive = 1"))

            // The unique index is in place.
            assertEquals(
                1L,
                migrated.scalarLong(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' " +
                        "AND name = 'index_vehicles_bluetoothAddress'",
                ),
            )

            migrated.close()
        }

    @Test
    fun `v3 to v4 activates a vehicle when none was active`() =
        runTest {
            val v3 = helper.createDatabase(3)
            v3.executeSQL(
                "INSERT INTO vehicles (id, name, bluetoothAddress, bluetoothName, isActive) " +
                    "VALUES (7, 'Only', 'AA:BB:CC:DD:EE:09', 'Car', 0)",
            )
            v3.close()

            val migrated = helper.runMigrationsAndValidate(4, listOf(MIGRATION_3_4))

            assertEquals(7L, migrated.scalarLong("SELECT id FROM vehicles"))
            assertEquals(1L, migrated.scalarLong("SELECT isActive FROM vehicles"))

            migrated.close()
        }

    @Test
    fun `v3 with no vehicles migrates without inventing one`() =
        runTest {
            val v3 = helper.createDatabase(3)
            v3.close()

            val migrated = helper.runMigrationsAndValidate(4, listOf(MIGRATION_3_4))

            assertEquals(0L, migrated.scalarLong("SELECT COUNT(*) FROM vehicles"))

            migrated.close()
        }

    @Test
    fun `v4 to v5 adds the address columns with every old spot unresolved`() =
        runTest {
            val v4 = helper.createDatabase(4)
            v4.executeSQL(
                "INSERT INTO vehicles (id, name, bluetoothAddress, bluetoothName, isActive) " +
                    "VALUES (1, 'Car', 'AA:BB:CC:DD:EE:01', 'Car', 1)",
            )
            v4.executeSQL(
                "INSERT INTO parking_locations (vehicleId, latitude, longitude, timestamp) " +
                    "VALUES (1, 42.0, -5.0, 100)",
            )
            v4.close()

            val migrated = helper.runMigrationsAndValidate(5, listOf(MIGRATION_4_5))

            // Existing rows keep their coordinates and are picked up by the app lookup.
            assertEquals(1L, migrated.scalarLong("SELECT COUNT(*) FROM parking_locations"))
            assertEquals(0L, migrated.scalarLong("SELECT addressResolved FROM parking_locations"))
            assertEquals(
                1L,
                migrated.scalarLong(
                    "SELECT COUNT(*) FROM parking_locations " +
                        "WHERE street IS NULL AND locality IS NULL",
                ),
            )

            // The new columns are writable.
            migrated.executeSQL(
                "UPDATE parking_locations SET street = 'Calle Mayor 12', " +
                    "locality = 'Getafe', addressResolved = 1 WHERE id = 1",
            )
            assertEquals(
                1L,
                migrated.scalarLong(
                    "SELECT addressResolved FROM parking_locations WHERE locality = 'Getafe'",
                ),
            )

            migrated.close()
        }
}
