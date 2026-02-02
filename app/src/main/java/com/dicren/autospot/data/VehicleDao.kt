package com.dicren.autospot.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles ORDER BY name ASC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE isActive = 1 LIMIT 1")
    fun getActiveVehicle(): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveVehicleSync(): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE bluetoothAddress = :bluetoothAddress LIMIT 1")
    suspend fun getVehicleByBluetoothAddress(bluetoothAddress: String): VehicleEntity?

    @Query("SELECT * FROM vehicles")
    suspend fun getAllVehiclesSync(): List<VehicleEntity>

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
}
