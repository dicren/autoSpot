package com.dicren.autospot.domain

import com.dicren.autospot.core.Clock
import com.dicren.autospot.data.ParkingRepository
import com.dicren.autospot.data.VehicleRepository
import com.dicren.autospot.location.LocationFix
import com.dicren.autospot.location.LocationSource

/**
 * Obtains a location and stores it for a vehicle.
 *
 * Rules:
 * - The specific vehicle wins; if it no longer exists the save fails instead of silently using
 *   another vehicle.
 * - A fresh fix is preferred. If it is unavailable, a last-known fix is accepted only while it is
 *   younger than [maxFixAgeMillis]: a ten-minute-old fix can be many kilometres away.
 */
class SaveParkingLocationUseCase(
    private val vehicleRepository: VehicleRepository,
    private val parkingRepository: ParkingRepository,
    private val locationSource: LocationSource,
    private val clock: Clock,
    private val hasLocationPermission: () -> Boolean,
    private val maxFixAgeMillis: Long = DEFAULT_MAX_FIX_AGE_MILLIS,
) {

    suspend fun save(vehicleId: Long? = null): SaveResult {
        if (!hasLocationPermission()) return SaveResult.NoPermission

        val vehicle = if (vehicleId != null) {
            vehicleRepository.getVehicleByIdOnce(vehicleId) ?: return SaveResult.NoVehicle
        } else {
            vehicleRepository.getActiveVehicleOnce() ?: return SaveResult.NoVehicle
        }

        val now = clock.nowMillis()
        val fix = try {
            usable(locationSource.currentLocation(), now)
                ?: usable(locationSource.lastLocation(), now)
        } catch (e: Exception) {
            return SaveResult.Error(e)
        } ?: return SaveResult.NoFix

        return try {
            parkingRepository.saveLocation(vehicle.id, fix.latitude, fix.longitude)
            SaveResult.Saved(
                vehicleId = vehicle.id,
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyMeters,
            )
        } catch (e: Exception) {
            SaveResult.Error(e)
        }
    }

    private fun usable(fix: LocationFix?, now: Long): LocationFix? {
        if (fix == null) return null
        // Unknown timestamps (some providers/mocks) are accepted rather than discarded.
        if (fix.timeMillis <= 0L) return fix
        return fix.takeIf { now - it.timeMillis <= maxFixAgeMillis }
    }

    companion object {
        const val DEFAULT_MAX_FIX_AGE_MILLIS = 5 * 60_000L
    }
}
