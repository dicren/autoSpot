package com.dicren.autospot.ui.parking

import android.content.Context
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dicren.autospot.data.AddVehicleResult
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.ParkingRepository
import com.dicren.autospot.data.SettingsRepository
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.data.VehicleRepository
import com.dicren.autospot.di.appContainer
import com.dicren.autospot.domain.AddressLookupOutcome
import com.dicren.autospot.domain.LocateSelf
import com.dicren.autospot.domain.ResolveLocationAddress
import com.dicren.autospot.domain.SaveParkingLocation
import com.dicren.autospot.domain.SaveResult
import com.dicren.autospot.location.LocationFix
import com.dicren.autospot.util.LocationAccuracy
import com.dicren.autospot.util.Permissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ParkingViewModel(
    private val vehicleRepository: VehicleRepository,
    private val parkingRepository: ParkingRepository,
    private val settingsRepository: SettingsRepository,
    private val saveParkingLocation: SaveParkingLocation,
    private val resolveLocationAddress: ResolveLocationAddress,
    private val locateSelf: LocateSelf,
    private val hasMonitoringPermission: () -> Boolean,
    private val locationAccuracy: () -> LocationAccuracy,
    private val hasBackgroundLocationPermission: () -> Boolean,
    private val isLocationEnabled: () -> Boolean,
) : ViewModel() {
    private data class Content(
        val vehicles: List<VehicleEntity>,
        val activeVehicle: VehicleEntity?,
        val locations: List<ParkingLocationEntity>,
        val allLocations: List<ParkingLocationEntity>,
        val selectedLocationId: Long?,
        val overview: MapOverview?,
    )

    private data class Flags(
        val hasMonitoringPermission: Boolean,
        val locationAccuracy: LocationAccuracy,
        val hasBackgroundLocationPermission: Boolean,
        val isLocationEnabled: Boolean,
        val isSaving: Boolean,
    )

    private val selectedLocationId = MutableStateFlow<Long?>(null)
    private val overview = MutableStateFlow<MapOverview?>(null)
    private val isSaving = MutableStateFlow(false)
    private val flags =
        MutableStateFlow(
            Flags(
                hasMonitoringPermission = hasMonitoringPermission(),
                locationAccuracy = locationAccuracy(),
                hasBackgroundLocationPermission = hasBackgroundLocationPermission(),
                isLocationEnabled = isLocationEnabled(),
                isSaving = false,
            ),
        )

    private val eventsFlow = MutableSharedFlow<ParkingEvent>(extraBufferCapacity = 4)
    val events = eventsFlow.asSharedFlow()

    /**
     * Requests to fill in missing addresses. Conflated on purpose: while a pass runs, more than one
     * request only means "look again", and the worker always reads the current list.
     */
    private val addressLookupRequests = Channel<Unit>(Channel.CONFLATED)

    private val locations: Flow<List<ParkingLocationEntity>> =
        vehicleRepository.activeVehicle.flatMapLatest { vehicle ->
            vehicle?.let { parkingRepository.locations(it.id) } ?: flowOf(emptyList())
        }

    private val allLocations: Flow<List<ParkingLocationEntity>> = parkingRepository.allLocations()

    private val content: Flow<Content> =
        combine(
            combine(
                vehicleRepository.vehicles,
                vehicleRepository.activeVehicle,
                locations,
                allLocations,
                selectedLocationId,
            ) { vehicles, active, locations, all, selected ->
                // `locations` is collected from the same active-vehicle flow, but `combine` can see
                // the new vehicle before its history flow emits. Filtering keeps the previous car's
                // spot from appearing under the new one for a frame.
                Content(
                    vehicles = vehicles,
                    activeVehicle = active,
                    locations = locations.filter { it.vehicleId == active?.id },
                    allLocations = all,
                    selectedLocationId = selected,
                    overview = null,
                )
            },
            overview,
        ) { content, mode -> content.copy(overview = mode) }

    val uiState: StateFlow<ParkingUiState> =
        combine(
            content,
            flags,
            settingsRepository.isBackgroundLocationRationaleShown,
        ) { content, flags, rationaleShown ->
            ParkingUiState(
                vehicles = content.vehicles,
                activeVehicle = content.activeVehicle,
                locations = content.locations,
                allLocations = content.allLocations,
                selectedLocationId = content.selectedLocationId,
                overview = content.overview,
                hasMonitoringPermission = flags.hasMonitoringPermission,
                locationAccuracy = flags.locationAccuracy,
                hasBackgroundLocationPermission = flags.hasBackgroundLocationPermission,
                isLocationEnabled = flags.isLocationEnabled,
                isSaving = flags.isSaving,
                backgroundLocationRationaleShown = rationaleShown,
                // Reaching this block means the repository flows emitted at least once.
                isReady = true,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ParkingUiState(),
        )

    init {
        // A history item belongs to the vehicle that was active when it was picked; switching
        // vehicles must not keep showing the previous car's spot. The overview is scoped to that
        // vehicle too (or to all of them), so it goes away with it.
        viewModelScope.launch {
            vehicleRepository.activeVehicle
                .map { it?.id }
                .distinctUntilChanged()
                .collect {
                    selectedLocationId.value = null
                    overview.value = null
                }
        }

        // Clearing the history, deleting the focused cluster or losing the vehicle must not leave
        // the overview framing spots that are no longer there.
        viewModelScope.launch {
            combine(overview, locations, allLocations) { mode, activeSpots, everySpot ->
                Triple(mode, activeSpots, everySpot)
            }.collect { (mode, activeSpots, everySpot) ->
                if (mode == null) return@collect
                val spots = if (mode.scope == MapScope.ALL_VEHICLES) everySpot else activeSpots
                when {
                    spots.isEmpty() -> closeOverview()

                    mode.clusterLocationIds != null &&
                        spots.none { it.id in mode.clusterLocationIds } -> clearClusterFocus()
                }
            }
        }

        // Address fill-in runs here, not in the save path: it is network work that can take
        // seconds, and saving the coordinates must never wait for it. A spot saved while the app
        // was closed gets its name the next time the app is opened.
        viewModelScope.launch {
            while (true) {
                addressLookupRequests.receive()
                try {
                    resolvePendingAddresses()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A storage failure must not kill the worker for the whole session.
                    Log.e(TAG, "Address lookup failed", e)
                }
            }
        }
        viewModelScope.launch {
            locations.collect { stored ->
                if (stored.any { !it.addressResolved }) addressLookupRequests.trySend(Unit)
            }
        }
        addressLookupRequests.trySend(Unit)
    }

    /**
     * One pass over the stored spots of the active vehicle, oldest requests included. Sequential
     * on purpose: the platform geocoder is a shared service and does not like bursts. A pass stops
     * at the first [AddressLookupOutcome.Unavailable], so an offline phone does not fire one failed
     * request per row; [refreshEnvironment] starts it again.
     */
    private suspend fun resolvePendingAddresses() {
        for (location in locations.first().filterNot { it.addressResolved }) {
            if (resolveLocationAddress.resolve(location) == AddressLookupOutcome.Unavailable) return
        }
    }

    /** Re-reads permissions and the system location switch; call on every resume. */
    fun refreshEnvironment() {
        flags.value =
            flags.value.copy(
                hasMonitoringPermission = hasMonitoringPermission(),
                locationAccuracy = locationAccuracy(),
                hasBackgroundLocationPermission = hasBackgroundLocationPermission(),
                isLocationEnabled = isLocationEnabled(),
            )

        // Coming back to the app is the natural moment to retry a lookup that had no network. The
        // pass itself needs a fresh read, so only ask when something is still pending.
        if (uiState.value.locations.any { !it.addressResolved }) {
            addressLookupRequests.trySend(Unit)
        }
    }

    /**
     * The best guess at where the user is standing, for the "go to my location" camera move.
     * Suspends while a fix is requested and never throws; null means "nothing to show".
     */
    suspend fun findMyLocation(): LocationFix? = locateSelf.find()

    /**
     * Shows a spot picked in the history. Picking the newest one clears the selection instead of
     * setting it, the same way a swipe back to it does: index 0 is what the card falls back to, and
     * keeping it selected would put a "back to latest" control on a card that already shows the
     * latest spot.
     */
    fun selectLocation(location: ParkingLocationEntity) {
        val isNewest =
            uiState.value.historyLocations
                .firstOrNull()
                ?.id == location.id
        selectedLocationId.value = if (isNewest) null else location.id
    }

    fun showLatestLocation() {
        selectedLocationId.value = null
    }

    /**
     * Opens the "every spot on the map" view. The active vehicle is left untouched, even for
     * [MapScope.ALL_VEHICLES]: the mode only stops showing it, so leaving the mode returns to the
     * car the user was looking at.
     */
    fun showOverviewOnMap(scope: MapScope) {
        selectedLocationId.value = null
        overview.value = MapOverview(scope)
    }

    /** Leaves the overview and shows the active vehicle's newest spot again. */
    fun closeOverview() {
        selectedLocationId.value = null
        overview.value = null
    }

    /**
     * Shows the card of a group of spots and pages only through them. The ids are frozen here: the
     * map re-clusters on every zoom, and the card must keep the group the user picked.
     */
    fun focusCluster(locationIds: Set<Long>) {
        if (locationIds.isEmpty() || overview.value == null) return
        selectedLocationId.value = null
        overview.value = overview.value?.copy(clusterLocationIds = locationIds)
    }

    /** Hides the card and shows the whole scope again, without leaving the overview. */
    fun clearClusterFocus() {
        selectedLocationId.value = null
        overview.value = overview.value?.copy(clusterLocationIds = null)
    }

    /** Deletes the spots of the focused group; the confirmation dialog calls this. */
    fun clearClusterHistory() {
        val ids = overview.value?.clusterLocationIds ?: return
        launchAction { parkingRepository.deleteLocations(ids) }
    }

    fun saveCurrentLocation() {
        // compareAndSet closes the window where two taps in the same frame both saw false.
        if (!isSaving.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val result = saveParkingLocation.save(null)
                eventsFlow.emit(
                    when (result) {
                        is SaveResult.Saved -> ParkingEvent.LocationSaved
                        SaveResult.NoVehicle -> ParkingEvent.NoVehicle
                        SaveResult.NoPermission -> ParkingEvent.PermissionMissing
                        SaveResult.NoFix, is SaveResult.Error -> ParkingEvent.SaveFailed
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The use case is total, but never let an unexpected failure take the process down.
                Log.e(TAG, "Manual location save failed", e)
                eventsFlow.emit(ParkingEvent.SaveFailed)
            } finally {
                // Also runs when the view model is cleared mid-save.
                isSaving.value = false
            }
        }
    }

    fun addVehicle(
        name: String,
        bluetoothAddress: String,
        bluetoothName: String,
        /** Receives true when the vehicle was stored, false when the address was taken. */
        onResult: (Boolean) -> Unit = {},
    ) {
        launchAction {
            val result = vehicleRepository.addVehicle(name, bluetoothAddress, bluetoothName)
            if (result is AddVehicleResult.Duplicate) {
                // The list and the active vehicle update themselves.
                eventsFlow.emit(ParkingEvent.DuplicateVehicle)
            }
            onResult(result is AddVehicleResult.Added)
        }
    }

    /** Records that the background-location explanation was shown, so it is not repeated. */
    fun markBackgroundRationaleShown() {
        launchAction { settingsRepository.setBackgroundLocationRationaleShown() }
    }

    /**
     * [onResult] runs after the repository answers, so the edit dialog can stay open when the
     * Bluetooth address is already taken instead of discarding what the user typed.
     */
    fun updateVehicle(
        vehicle: VehicleEntity,
        onResult: (Boolean) -> Unit = {},
    ) {
        launchAction {
            val saved = vehicleRepository.updateVehicle(vehicle)
            if (!saved) eventsFlow.emit(ParkingEvent.VehicleUpdateConflict)
            onResult(saved)
        }
    }

    fun deleteVehicle(vehicle: VehicleEntity) {
        launchAction { vehicleRepository.deleteVehicle(vehicle.id) }
    }

    fun activateVehicle(vehicle: VehicleEntity) {
        launchAction { vehicleRepository.activateVehicle(vehicle.id) }
    }

    fun deleteLocation(location: ParkingLocationEntity) {
        launchAction {
            parkingRepository.deleteLocation(location.id)
            if (selectedLocationId.value == location.id) selectedLocationId.value = null
        }
    }

    /**
     * Clears the history of [vehicleId], or of the active vehicle when no id is passed.
     *
     * The screen passes the vehicle it is showing: resolving the active vehicle here would clear
     * the wrong history if the user switches vehicles while the confirmation dialog is open.
     */
    fun clearHistory(vehicleId: Long? = null) {
        launchAction {
            val id = vehicleId ?: vehicleRepository.getActiveVehicleOnce()?.id
            if (id != null) parkingRepository.clearLocations(id)
        }
    }

    /**
     * Runs a repository action without letting a storage failure crash the app. Cancellation is
     * rethrown so viewModelScope teardown still works.
     */
    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Parking action failed", e)
                eventsFlow.emit(ParkingEvent.OperationFailed)
            }
        }
    }

    companion object {
        private const val TAG = "ParkingViewModel"

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val container = appContext.appContainer
                    ParkingViewModel(
                        vehicleRepository = container.vehicleRepository,
                        parkingRepository = container.parkingRepository,
                        settingsRepository = container.settingsRepository,
                        saveParkingLocation = container.saveParkingLocationUseCase,
                        resolveLocationAddress = container.resolveLocationAddressUseCase,
                        locateSelf = container.locateSelf,
                        hasMonitoringPermission = { Permissions.canMonitor(appContext) },
                        locationAccuracy = { Permissions.locationAccuracy(appContext) },
                        hasBackgroundLocationPermission = { Permissions.hasBackgroundLocation(appContext) },
                        isLocationEnabled = { isLocationEnabled(appContext) },
                    )
                }
            }
        }

        private fun isLocationEnabled(context: Context): Boolean {
            val manager = context.getSystemService(LocationManager::class.java) ?: return true
            return manager.isLocationEnabled
        }
    }
}
