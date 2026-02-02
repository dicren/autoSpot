package com.dicren.autospot.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkingLocationDao {

    @Query("SELECT * FROM parking_locations WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    fun getLocationsByVehicle(vehicleId: Long): Flow<List<ParkingLocationEntity>>

    @Query("SELECT * FROM parking_locations WHERE vehicleId = :vehicleId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestLocationByVehicle(vehicleId: Long): Flow<ParkingLocationEntity?>

    @Query("SELECT * FROM parking_locations WHERE vehicleId = :vehicleId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLocationByVehicleSync(vehicleId: Long): ParkingLocationEntity?

    @Insert
    suspend fun insertLocation(location: ParkingLocationEntity)

    @Query("DELETE FROM parking_locations WHERE id = :id")
    suspend fun deleteLocation(id: Long)

    @Query("DELETE FROM parking_locations WHERE vehicleId = :vehicleId")
    suspend fun deleteAllLocationsByVehicle(vehicleId: Long)
}
