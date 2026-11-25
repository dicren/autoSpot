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
import com.dicren.autospot.domain.SaveParkingLocation
import com.dicren.autospot.domain.SaveResult
import com.dicren.autospot.util.LocationAccuracy
import com.dicren.autospot.util.Permissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val hasMonitoringPermission: () -> Boolean,
    private val locationAccuracy: () -> LocationAccuracy,
    private val hasBackgroundLocationPermission: () -> Boolean,
    private val isLocationEnabled: () -> Boolean,
) : ViewModel() {
    private data class Content(
        val vehicles: List<VehicleEntity>,
        val activeVehicle: VehicleEntity?,
        val locations: List<ParkingLocationEntity>,
        val selectedLocationId: Long?,
    )

    private data class Flags(
        val hasMonitoringPermission: Boolean,
        val locationAccuracy: LocationAccuracy,
        val hasBackgroundLocationPermission: Boolean,
        val isLocationEnabled: Boolean,
        val isSaving: Boolean,
    )

    private val selectedLocationId = MutableStateFlow<Long?>(null)
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

    private val locations: Flow<List<ParkingLocationEntity>> =
        vehicleRepository.activeVehicle.flatMapLatest { vehicle ->
            vehicle?.let { parkingRepository.locations(it.id) } ?: flowOf(emptyList())
        }

    private val content: Flow<Content> =
        combine(
            vehicleRepository.vehicles,
            vehicleRepository.activeVehicle,
            locations,
            selectedLocationId,
        ) { vehicles, active, locations, selected ->
            Content(vehicles, active, locations, selected)
        }

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
                selectedLocationId = content.selectedLocationId,
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
        // vehicles must not keep showing the previous car's spot.
        viewModelScope.launch {
            vehicleRepository.activeVehicle
                .map { it?.id }
                .distinctUntilChanged()
                .collect { selectedLocationId.value = null }
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
    }

    fun selectLocation(location: ParkingLocationEntity) {
        selectedLocationId.value = location.id
    }

    fun showLatestLocation() {
        selectedLocationId.value = null
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
