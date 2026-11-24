package com.dicren.autospot.data

import com.dicren.autospot.core.Clock
import kotlinx.coroutines.flow.Flow

interface ParkingRepository {
    fun locations(vehicleId: Long): Flow<List<ParkingLocationEntity>>

    fun latestLocation(vehicleId: Long): Flow<ParkingLocationEntity?>

    suspend fun saveLocation(
        vehicleId: Long,
        latitude: Double,
        longitude: Double,
    ): Long

    suspend fun deleteLocation(id: Long)

    suspend fun clearLocations(vehicleId: Long)

    suspend fun latestLocationOnce(vehicleId: Long): ParkingLocationEntity?

    companion object {
        /** Per-vehicle history cap; the table would otherwise grow forever. */
        const val MAX_LOCATIONS_PER_VEHICLE = 100
    }
}

class RoomParkingRepository(
    private val dao: ParkingLocationDao,
    private val clock: Clock,
) : ParkingRepository {
    override fun locations(vehicleId: Long): Flow<List<ParkingLocationEntity>> = dao.getLocationsByVehicle(vehicleId)

    override fun latestLocation(vehicleId: Long): Flow<ParkingLocationEntity?> =
        dao.getLatestLocationByVehicle(vehicleId)

    override suspend fun saveLocation(
        vehicleId: Long,
        latitude: Double,
        longitude: Double,
    ): Long =
        dao.insertAndPrune(
            ParkingLocationEntity(
                vehicleId = vehicleId,
                latitude = latitude,
                longitude = longitude,
                timestamp = clock.nowMillis(),
            ),
            ParkingRepository.MAX_LOCATIONS_PER_VEHICLE,
        )

    override suspend fun deleteLocation(id: Long) = dao.deleteLocation(id)

    override suspend fun clearLocations(vehicleId: Long) = dao.deleteAllLocationsByVehicle(vehicleId)

    override suspend fun latestLocationOnce(vehicleId: Long): ParkingLocationEntity? =
        dao.getLatestLocationByVehicleOnce(vehicleId)
}
