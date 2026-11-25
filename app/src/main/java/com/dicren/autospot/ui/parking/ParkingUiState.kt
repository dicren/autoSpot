package com.dicren.autospot.ui.parking

import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.util.LocationAccuracy

/** Everything the parking screen draws, derived from the repositories. */
data class ParkingUiState(
    val vehicles: List<VehicleEntity> = emptyList(),
    val activeVehicle: VehicleEntity? = null,
    /** Newest first. */
    val locations: List<ParkingLocationEntity> = emptyList(),
    val selectedLocationId: Long? = null,
    val hasMonitoringPermission: Boolean = false,
    val locationAccuracy: LocationAccuracy = LocationAccuracy.NONE,
    val hasBackgroundLocationPermission: Boolean = false,
    val isLocationEnabled: Boolean = true,
    val isSaving: Boolean = false,
    /** True once the persisted "allow all the time" explanation has been shown. */
    val backgroundLocationRationaleShown: Boolean = false,
    /** False until the first real emission; the defaults must not drive service or banners. */
    val isReady: Boolean = false,
) {
    val latestLocation: ParkingLocationEntity? get() = locations.firstOrNull()

    val selectedLocation: ParkingLocationEntity?
        get() = selectedLocationId?.let { id -> locations.firstOrNull { it.id == id } }

    val displayLocation: ParkingLocationEntity? get() = selectedLocation ?: latestLocation

    val isShowingHistory: Boolean get() = selectedLocation != null

    /** Index of the displayed spot in [locations]; 0 is the newest one. */
    val displayIndex: Int
        get() {
            val id = displayLocation?.id ?: return 0
            return locations.indexOfFirst { it.id == id }.coerceAtLeast(0)
        }

    /** True when there is an older spot behind the displayed one. */
    val canStepOlder: Boolean get() = displayIndex < locations.lastIndex

    /** True when the newest spot is not the displayed one. */
    val canStepNewer: Boolean get() = displayIndex > 0

    /**
     * Index of the spot one swipe away in [locations], or null when the swipe would leave the
     * history. Index 0 is the latest spot, which the screen represents by clearing the selection.
     */
    fun stepIndex(older: Boolean): Int? = (displayIndex + if (older) 1 else -1).takeIf { it in locations.indices }

    /** Precise location is what the monitor and the save use case need. */
    val hasLocationPermission: Boolean get() = locationAccuracy == LocationAccuracy.PRECISE

    /** The user granted location but picked "Approximate". */
    val isLocationApproximate: Boolean get() = locationAccuracy == LocationAccuracy.COARSE

    /**
     * True when the app must explain the "Allow all the time" requirement before sending the
     * user to the system background-location screen.
     *
     * It is only asked once, after precise location is granted and a vehicle exists: there is no
     * point in requesting background access before there is something to monitor.
     */
    val needsBackgroundLocationRationale: Boolean
        get() =
            isReady &&
                hasLocationPermission &&
                !hasBackgroundLocationPermission &&
                vehicles.isNotEmpty() &&
                !backgroundLocationRationaleShown
}

/** One-shot things to tell the user, shown as a snackbar. */
sealed interface ParkingEvent {
    data object LocationSaved : ParkingEvent

    data object SaveFailed : ParkingEvent

    data object NoVehicle : ParkingEvent

    /** The location permission disappeared between the tap and the save. */
    data object PermissionMissing : ParkingEvent

    data object DuplicateVehicle : ParkingEvent

    data object VehicleUpdateConflict : ParkingEvent

    /** A repository write failed (storage error, database closed, ...). */
    data object OperationFailed : ParkingEvent
}
