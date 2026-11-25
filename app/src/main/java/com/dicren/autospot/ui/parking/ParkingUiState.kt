package com.dicren.autospot.ui.parking

import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.util.LocationAccuracy

/** Whose spots the "every spot on the map" view draws. */
enum class MapScope {
    /** Only the active vehicle's history. */
    ACTIVE_VEHICLE,

    /** Every vehicle's history; no vehicle is being shown. */
    ALL_VEHICLES,
}

/**
 * The "every spot on the map" view, opened from the history sheet.
 *
 * [clusterLocationIds] is the group the user tapped, frozen at that moment: the map re-clusters on
 * every zoom, and the card has to keep paging the spots the user actually picked. Null means no
 * group has been picked yet, so the map shows every spot of [scope] and there is no card.
 */
data class MapOverview(
    val scope: MapScope,
    val clusterLocationIds: Set<Long>? = null,
)

/** Everything the parking screen draws, derived from the repositories. */
data class ParkingUiState(
    val vehicles: List<VehicleEntity> = emptyList(),
    val activeVehicle: VehicleEntity? = null,
    /** Newest first, active vehicle only. */
    val locations: List<ParkingLocationEntity> = emptyList(),
    /** Newest first, every vehicle. Only the overview draws these. */
    val allLocations: List<ParkingLocationEntity> = emptyList(),
    val selectedLocationId: Long? = null,
    /** Non-null while the map shows every spot instead of a single one. */
    val overview: MapOverview? = null,
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

    /** True while the map draws every spot of a scope. */
    val isOverviewing: Boolean get() = overview != null

    /** True when the overview is not tied to the active vehicle, so the top card must say so. */
    val isShowingAllVehicles: Boolean get() = overview?.scope == MapScope.ALL_VEHICLES

    /** Every spot the overview draws, newest first; empty outside the overview. */
    val overviewLocations: List<ParkingLocationEntity>
        get() = if (isShowingAllVehicles) allLocations else locations

    /** True once a group of spots has been picked in the overview. */
    val isClusterFocused: Boolean get() = overview?.clusterLocationIds != null

    /**
     * The history the card and the sheet operate on: the whole scope, or only the tapped group.
     * Outside the overview it is the active vehicle's history, exactly as before.
     */
    val historyLocations: List<ParkingLocationEntity>
        get() =
            overview?.clusterLocationIds?.let { ids ->
                overviewLocations.filter { it.id in ids }
            } ?: locations

    val selectedLocation: ParkingLocationEntity?
        get() = selectedLocationId?.let { id -> historyLocations.firstOrNull { it.id == id } }

    /**
     * The spot the card shows. In the overview nothing is shown until a group is picked: the whole
     * point of the mode is to see every spot, not one of them.
     */
    val displayLocation: ParkingLocationEntity?
        get() =
            when {
                isOverviewing && !isClusterFocused -> null
                else -> selectedLocation ?: historyLocations.firstOrNull()
            }

    val isShowingHistory: Boolean get() = selectedLocation != null

    /** Index of the displayed spot in [historyLocations]; 0 is the newest one. */
    val displayIndex: Int
        get() {
            val id = displayLocation?.id ?: return 0
            return historyLocations.indexOfFirst { it.id == id }.coerceAtLeast(0)
        }

    /** True when there is an older spot behind the displayed one. */
    val canStepOlder: Boolean get() = displayIndex < historyLocations.lastIndex

    /** True when the newest spot is not the displayed one. */
    val canStepNewer: Boolean get() = displayIndex > 0

    /**
     * Index of the spot one swipe away in [historyLocations], or null when the swipe would leave
     * the history. Index 0 is the latest spot, which the screen represents by clearing the
     * selection.
     */
    fun stepIndex(older: Boolean): Int? =
        (displayIndex + if (older) 1 else -1).takeIf { it in historyLocations.indices }

    /**
     * The newest spot of each vehicle, drawn with the car glyph so the overview says at a glance
     * where every car is parked.
     */
    val latestSpotIds: Set<Long>
        get() = overviewLocations.groupBy { it.vehicleId }.values.mapNotNullTo(mutableSetOf()) { it.firstOrNull()?.id }

    /** Vehicle names by id, so the overview history can say which car each spot belongs to. */
    val vehicleNames: Map<Long, String> get() = vehicles.associate { it.id to it.name }

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
