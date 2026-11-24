package com.dicren.autospot.ui.parking

import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.VehicleEntity

/** Everything the parking screen draws, derived from the repositories. */
data class ParkingUiState(
    val vehicles: List<VehicleEntity> = emptyList(),
    val activeVehicle: VehicleEntity? = null,
    /** Newest first. */
    val locations: List<ParkingLocationEntity> = emptyList(),
    val selectedLocationId: Long? = null,
    val hasMonitoringPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val isLocationEnabled: Boolean = true,
    val isSaving: Boolean = false,
) {
    val latestLocation: ParkingLocationEntity? get() = locations.firstOrNull()

    val selectedLocation: ParkingLocationEntity?
        get() = selectedLocationId?.let { id -> locations.firstOrNull { it.id == id } }

    val displayLocation: ParkingLocationEntity? get() = selectedLocation ?: latestLocation

    val isShowingHistory: Boolean get() = selectedLocation != null
}

/** One-shot things to tell the user, shown as a snackbar. */
sealed interface ParkingEvent {
    data object LocationSaved : ParkingEvent
    data object SaveFailed : ParkingEvent
    data object NoVehicle : ParkingEvent
    data object DuplicateVehicle : ParkingEvent
    data object VehicleUpdateConflict : ParkingEvent
    data object MapsNotInstalled : ParkingEvent
}
