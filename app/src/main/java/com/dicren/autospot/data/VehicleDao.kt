package com.dicren.autospot.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY name ASC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE isActive = 1 LIMIT 1")
    fun getActiveVehicle(): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveVehicleOnce(): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE bluetoothAddress = :bluetoothAddress LIMIT 1")
    suspend fun getVehicleByBluetoothAddress(bluetoothAddress: String): VehicleEntity?

    @Query("SELECT * FROM vehicles ORDER BY name ASC")
    suspend fun getAllVehiclesOnce(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getVehicleById(id: Long): VehicleEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM vehicles WHERE isActive = 1)")
    suspend fun hasActiveVehicle(): Boolean

    @Query("SELECT * FROM vehicles ORDER BY name ASC LIMIT 1")
    suspend fun firstVehicle(): VehicleEntity?

    /**
     * Returns the new row id, or -1 when the Bluetooth address already exists.
     *
     * `INSERT OR IGNORE` is deliberate: the constraint failure comes from the configured
     * SQLite driver, and the bundled driver does not throw `SQLiteConstraintException` like the
     * platform one. Ignoring the row keeps duplicate detection driver-independent.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVehicle(vehicle: VehicleEntity): Long

    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteVehicle(id: Long)

    @Query("UPDATE vehicles SET isActive = 0")
    suspend fun deactivateAllVehicles()

    @Query("UPDATE vehicles SET isActive = 1 WHERE id = :id")
    suspend fun activateVehicle(id: Long)

    /** Makes [id] the only active vehicle. Deactivating and activating must not be split. */
    @Transaction
    suspend fun activateOnly(id: Long) {
        deactivateAllVehicles()
        activateVehicle(id)
    }

    /** Inserts the vehicle and makes it active, all in one transaction. Returns -1 on conflict. */
    @Transaction
    suspend fun insertVehicleAndActivate(vehicle: VehicleEntity): Long {
        val id = insertVehicle(vehicle)
        if (id == -1L) return -1L
        activateOnly(id)
        return id
    }

    /**
     * Deletes a vehicle and, if it was the active one, activates another so the app never stays
     * with vehicles but no active vehicle. Parking locations go with it via the foreign key.
     */
    @Transaction
    suspend fun deleteVehicleAndKeepOneActive(id: Long) {
        val wasActive = getVehicleById(id)?.isActive == true
        deleteVehicle(id)
        if (wasActive && !hasActiveVehicle()) {
            firstVehicle()?.let { activateVehicle(it.id) }
        }
    }
}
