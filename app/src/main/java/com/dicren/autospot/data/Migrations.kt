package com.dicren.autospot.data

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL

/**
 * v3 → v4: the Bluetooth address becomes unique and the "one active vehicle" invariant is
 * repaired for existing installs.
 *
 * Duplicates may already exist in the wild (the app allowed adding the same device twice), so
 * the migration first moves their parking history to the surviving row, then removes them.
 */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override suspend fun migrate(connection: SQLiteConnection) {
            // 1. Reassign the history of duplicate vehicles to the survivor (lowest id per address).
            connection.executeSQL(
                """
                UPDATE parking_locations
                SET vehicleId = (
                    SELECT MIN(survivor.id)
                    FROM vehicles AS survivor
                    WHERE survivor.bluetoothAddress = (
                        SELECT duplicate.bluetoothAddress
                        FROM vehicles AS duplicate
                        WHERE duplicate.id = parking_locations.vehicleId
                    )
                )
                WHERE vehicleId NOT IN (
                    SELECT MIN(id) FROM vehicles GROUP BY bluetoothAddress
                )
                """.trimIndent(),
            )

            // 2. Drop the duplicate vehicles.
            connection.executeSQL(
                """
                DELETE FROM vehicles
                WHERE id NOT IN (SELECT MIN(id) FROM vehicles GROUP BY bluetoothAddress)
                """.trimIndent(),
            )

            // 3. Enforce uniqueness from now on.
            connection.executeSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_bluetoothAddress` " +
                    "ON `vehicles` (`bluetoothAddress`)",
            )

            // 4. Keep at most one active vehicle.
            connection.executeSQL(
                """
                UPDATE vehicles SET isActive = 0
                WHERE isActive = 1
                  AND id NOT IN (SELECT MIN(id) FROM vehicles WHERE isActive = 1)
                """.trimIndent(),
            )

            // 5. If there are vehicles but none is active, activate the first one.
            connection.executeSQL(
                """
                UPDATE vehicles SET isActive = 1
                WHERE id = (SELECT MIN(id) FROM vehicles)
                  AND NOT EXISTS (SELECT 1 FROM vehicles WHERE isActive = 1)
                """.trimIndent(),
            )
        }
    }
