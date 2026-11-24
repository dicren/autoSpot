package com.dicren.autospot.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkingLocationDao {
    @Query("SELECT * FROM parking_locations WHERE vehicleId = :vehicleId ORDER BY timestamp DESC, id DESC")
    fun getLocationsByVehicle(vehicleId: Long): Flow<List<ParkingLocationEntity>>

    @Query("SELECT * FROM parking_locations WHERE vehicleId = :vehicleId ORDER BY timestamp DESC, id DESC LIMIT 1")
    fun getLatestLocationByVehicle(vehicleId: Long): Flow<ParkingLocationEntity?>

    @Query("SELECT * FROM parking_locations WHERE vehicleId = :vehicleId ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getLatestLocationByVehicleOnce(vehicleId: Long): ParkingLocationEntity?

    @Insert
    suspend fun insertLocation(location: ParkingLocationEntity): Long

    @Query("DELETE FROM parking_locations WHERE id = :id")
    suspend fun deleteLocation(id: Long)

    @Query("DELETE FROM parking_locations WHERE vehicleId = :vehicleId")
    suspend fun deleteAllLocationsByVehicle(vehicleId: Long)

    /** Keeps the newest [keep] rows of a vehicle; the history is append-only and unbounded. */
    @Query(
        """
        DELETE FROM parking_locations
        WHERE vehicleId = :vehicleId
          AND id NOT IN (
              SELECT id FROM parking_locations
              WHERE vehicleId = :vehicleId
              ORDER BY timestamp DESC, id DESC
              LIMIT :keep
          )
        """,
    )
    suspend fun pruneTo(
        vehicleId: Long,
        keep: Int,
    )

    @Transaction
    suspend fun insertAndPrune(
        location: ParkingLocationEntity,
        keep: Int,
    ): Long {
        val id = insertLocation(location)
        pruneTo(location.vehicleId, keep)
        return id
    }
}
