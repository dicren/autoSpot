package com.dicren.autospot.domain

import com.dicren.autospot.core.Clock
import com.dicren.autospot.data.ParkingRepository
import com.dicren.autospot.data.VehicleRepository
import com.dicren.autospot.location.LocationFix
import com.dicren.autospot.location.LocationSource
import kotlin.coroutines.cancellation.CancellationException

/**
 * Obtains a location and stores it for a vehicle.
 *
 * Rules:
 * - The specific vehicle wins; if it no longer exists the save fails instead of silently using
 *   another vehicle.
 * - A fresh fix is preferred. If it is unavailable, a last-known fix is accepted only while it is
 *   younger than [maxFixAgeMillis]: a ten-minute-old fix can be many kilometres away.
 * - A fix whose reported accuracy is worse than [maxAccuracyMeters] is discarded. Saving a
 *   two-kilometre cell-tower fix as "where the car is" is worse than reporting a failure.
 * - Each source is asked independently. A failure while requesting the fresh fix must not skip
 *   the last known one: Play Services can reject the request while still holding a usable fix.
 * - The whole operation is total: any storage or permission-check failure becomes
 *   [SaveResult.Error] instead of escaping to a service or view-model coroutine, where it would
 *   leave the foreground service running or crash the process.
 */
interface SaveParkingLocation {
    suspend fun save(vehicleId: Long? = null): SaveResult
}

class SaveParkingLocationUseCase(
    private val vehicleRepository: VehicleRepository,
    private val parkingRepository: ParkingRepository,
    private val locationSource: LocationSource,
    private val clock: Clock,
    private val hasLocationPermission: () -> Boolean,
    private val maxFixAgeMillis: Long = DEFAULT_MAX_FIX_AGE_MILLIS,
    private val maxAccuracyMeters: Float = DEFAULT_MAX_ACCURACY_METERS,
) : SaveParkingLocation {
    override suspend fun save(vehicleId: Long?): SaveResult =
        try {
            if (!hasLocationPermission()) return SaveResult.NoPermission

            val vehicle =
                if (vehicleId != null) {
                    vehicleRepository.getVehicleByIdOnce(vehicleId) ?: return SaveResult.NoVehicle
                } else {
                    vehicleRepository.getActiveVehicleOnce() ?: return SaveResult.NoVehicle
                }

            val now = clock.nowMillis()
            val current = fetchFix(now) { locationSource.currentLocation() }
            val last = if (current.getOrNull() == null) fetchFix(now) { locationSource.lastLocation() } else null
            val fix = current.getOrNull() ?: last?.getOrNull()
            if (fix == null) {
                val error = last?.exceptionOrNull() ?: current.exceptionOrNull()
                return if (error != null) SaveResult.Error(error) else SaveResult.NoFix
            }

            parkingRepository.saveLocation(vehicle.id, fix.latitude, fix.longitude)
            SaveResult.Saved(
                vehicleId = vehicle.id,
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyMeters,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SaveResult.Error(e)
        }

    /**
     * Requests one fix, turning an expected failure into an empty result instead of aborting the
     * whole save. Cancellation is never swallowed: the caller is being torn down.
     */
    private suspend fun fetchFix(
        now: Long,
        fetch: suspend () -> LocationFix?,
    ): Result<LocationFix?> =
        try {
            Result.success(usable(fetch(), now))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun usable(
        fix: LocationFix?,
        now: Long,
    ): LocationFix? {
        if (fix == null) return null
        // Unknown timestamps (some providers/mocks) are accepted rather than discarded.
        if (fix.timeMillis > 0L && now - fix.timeMillis > maxFixAgeMillis) return null
        // Unknown accuracy is accepted; a known poor accuracy is not.
        if (fix.accuracyMeters != null && fix.accuracyMeters > maxAccuracyMeters) return null
        return fix
    }

    companion object {
        const val DEFAULT_MAX_FIX_AGE_MILLIS = 5 * 60_000L
        const val DEFAULT_MAX_ACCURACY_METERS = 100f
    }
}
