package com.dicren.autospot.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Insert
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

    /** Inserts the vehicle and makes it active, all in one transaction. */
    @Transaction
    suspend fun insertVehicleAndActivate(vehicle: VehicleEntity): Long {
        val id = insertVehicle(vehicle)
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
