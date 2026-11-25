package com.dicren.autospot.domain

import com.dicren.autospot.location.LocationFix
import com.dicren.autospot.location.LocationSource
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where the user is right now, for the "go to my location" camera move.
 *
 * The last known fix comes first because it is instant and it is exactly what the blue dot on the
 * map already shows, so the button takes the user to the dot they can see. A fresh fix is only
 * requested when there is nothing cached. Nothing is stored, and the fix is not validated the way
 * a saved spot is: this never claims "your car is here".
 */
interface LocateSelf {
    suspend fun find(): LocationFix?
}

class LocateSelfUseCase(
    private val locationSource: LocationSource,
    private val hasLocationPermission: () -> Boolean,
) : LocateSelf {
    override suspend fun find(): LocationFix? =
        try {
            if (!hasLocationPermission()) return null
            locationSource.lastLocation() ?: locationSource.currentLocation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
}
