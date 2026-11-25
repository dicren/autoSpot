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

    /**
     * Every stored spot of every vehicle, newest first. The "every spot on the map" view draws
     * them together, so it cannot reuse the per-vehicle query.
     */
    @Query("SELECT * FROM parking_locations ORDER BY timestamp DESC, id DESC")
    fun getAllLocations(): Flow<List<ParkingLocationEntity>>

    @Query("SELECT * FROM parking_locations WHERE vehicleId = :vehicleId ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getLatestLocationByVehicleOnce(vehicleId: Long): ParkingLocationEntity?

    @Insert
    suspend fun insertLocation(location: ParkingLocationEntity): Long

    @Query("DELETE FROM parking_locations WHERE id = :id")
    suspend fun deleteLocation(id: Long)

    /** Deletes a group of spots at once; the overview clears a cluster this way. */
    @Query("DELETE FROM parking_locations WHERE id IN (:ids)")
    suspend fun deleteLocations(ids: List<Long>)

    /** Stores the resolved address and marks the row as attempted, even when no address was found. */
    @Query(
        """
        UPDATE parking_locations
        SET street = :street, locality = :locality, addressResolved = 1
        WHERE id = :id
        """,
    )
    suspend fun updateAddress(
        id: Long,
        street: String?,
        locality: String?,
    )

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
