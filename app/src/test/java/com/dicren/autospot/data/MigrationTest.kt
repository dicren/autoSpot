package com.dicren.autospot.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    /** Inserts a v3 database with duplicate Bluetooth addresses and their history. */
    private fun createV3WithDuplicates(): androidx.sqlite.db.SupportSQLiteDatabase {
        val db = helper.createDatabase(AppDatabase.DATABASE_NAME, 3)
        db.execSQL(
            """
            INSERT INTO vehicles (id, name, bluetoothAddress, bluetoothName, isActive) VALUES
                (1, 'First',  'AA:BB:CC:DD:EE:01', 'Car', 0),
                (2, 'Second', 'AA:BB:CC:DD:EE:01', 'Car', 1),
                (3, 'Other',  'AA:BB:CC:DD:EE:02', 'Other', 1)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO parking_locations (vehicleId, latitude, longitude, timestamp) VALUES
                (1, 1.0, 1.0, 100),
                (2, 2.0, 2.0, 200)
            """.trimIndent(),
        )
        return db
    }

    @Test
    fun `v3 to v4 merges duplicate vehicles and keeps their history`() {
        val v3 = createV3WithDuplicates()
        v3.close()

        val migrated =
            helper.runMigrationsAndValidate(
                AppDatabase.DATABASE_NAME,
                4,
                true,
                MIGRATION_3_4,
            )

        migrated.query("SELECT COUNT(*) FROM vehicles").use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
        }

        // The two locations now belong to the surviving row (lowest id).
        migrated.query("SELECT vehicleId FROM parking_locations ORDER BY timestamp").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
            cursor.moveToNext()
            assertEquals(1, cursor.getInt(0))
        }

        // Only one vehicle remains active after the migration.
        migrated.query("SELECT COUNT(*) FROM vehicles WHERE isActive = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }

        // The unique index is in place.
        migrated
            .query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' " +
                    "AND name = 'index_vehicles_bluetoothAddress'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
    }

    @Test
    fun `v3 to v4 activates a vehicle when none was active`() {
        val v3 = helper.createDatabase(AppDatabase.DATABASE_NAME, 3)
        v3.execSQL(
            "INSERT INTO vehicles (id, name, bluetoothAddress, bluetoothName, isActive) " +
                "VALUES (7, 'Only', 'AA:BB:CC:DD:EE:09', 'Car', 0)",
        )
        v3.close()

        val migrated =
            helper.runMigrationsAndValidate(
                AppDatabase.DATABASE_NAME,
                4,
                true,
                MIGRATION_3_4,
            )

        migrated.query("SELECT id, isActive FROM vehicles").use { cursor ->
            cursor.moveToFirst()
            assertEquals(7, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
        }
    }

    @Test
    fun `v3 with no vehicles migrates without inventing one`() {
        val v3 = helper.createDatabase(AppDatabase.DATABASE_NAME, 3)
        v3.close()

        val migrated =
            helper.runMigrationsAndValidate(
                AppDatabase.DATABASE_NAME,
                4,
                true,
                MIGRATION_3_4,
            )

        migrated.query("SELECT COUNT(*) FROM vehicles").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        assertNotNull(migrated)
    }
}
