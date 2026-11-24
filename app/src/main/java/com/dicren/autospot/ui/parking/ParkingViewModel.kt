package com.dicren.autospot.ui.parking

import android.content.Context
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dicren.autospot.data.AddVehicleResult
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.ParkingRepository
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.data.VehicleRepository
import com.dicren.autospot.di.appContainer
import com.dicren.autospot.domain.SaveParkingLocationUseCase
import com.dicren.autospot.domain.SaveResult
import com.dicren.autospot.util.Permissions
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
    private val saveParkingLocation: SaveParkingLocationUseCase,
    private val hasMonitoringPermission: () -> Boolean,
    private val hasLocationPermission: () -> Boolean,
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
        val hasLocationPermission: Boolean,
        val isLocationEnabled: Boolean,
        val isSaving: Boolean,
    )

    private val selectedLocationId = MutableStateFlow<Long?>(null)
    private val isSaving = MutableStateFlow(false)
    private val flags =
        MutableStateFlow(
            Flags(
                hasMonitoringPermission = hasMonitoringPermission(),
                hasLocationPermission = hasLocationPermission(),
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
        combine(content, flags) { content, flags ->
            ParkingUiState(
                vehicles = content.vehicles,
                activeVehicle = content.activeVehicle,
                locations = content.locations,
                selectedLocationId = content.selectedLocationId,
                hasMonitoringPermission = flags.hasMonitoringPermission,
                hasLocationPermission = flags.hasLocationPermission,
                isLocationEnabled = flags.isLocationEnabled,
                isSaving = flags.isSaving,
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
                hasLocationPermission = hasLocationPermission(),
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
        if (isSaving.value) return
        viewModelScope.launch {
            isSaving.value = true
            val result = saveParkingLocation.save(null)
            isSaving.value = false
            eventsFlow.emit(
                when (result) {
                    is SaveResult.Saved -> ParkingEvent.LocationSaved
                    SaveResult.NoVehicle -> ParkingEvent.NoVehicle
                    SaveResult.NoPermission, SaveResult.NoFix, is SaveResult.Error -> ParkingEvent.SaveFailed
                },
            )
        }
    }

    fun addVehicle(
        name: String,
        bluetoothAddress: String,
        bluetoothName: String,
    ) {
        viewModelScope.launch {
            when (vehicleRepository.addVehicle(name, bluetoothAddress, bluetoothName)) {
                is AddVehicleResult.Added -> Unit

                // The list and the active vehicle update themselves.
                is AddVehicleResult.Duplicate -> eventsFlow.emit(ParkingEvent.DuplicateVehicle)
            }
        }
    }

    fun updateVehicle(vehicle: VehicleEntity) {
        viewModelScope.launch {
            if (!vehicleRepository.updateVehicle(vehicle)) {
                eventsFlow.emit(ParkingEvent.VehicleUpdateConflict)
            }
        }
    }

    fun deleteVehicle(vehicle: VehicleEntity) {
        viewModelScope.launch { vehicleRepository.deleteVehicle(vehicle.id) }
    }

    fun activateVehicle(vehicle: VehicleEntity) {
        viewModelScope.launch { vehicleRepository.activateVehicle(vehicle.id) }
    }

    fun deleteLocation(location: ParkingLocationEntity) {
        viewModelScope.launch {
            parkingRepository.deleteLocation(location.id)
            if (selectedLocationId.value == location.id) selectedLocationId.value = null
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            // Not uiState.value: the state flow stops emitting while nobody is collecting.
            val vehicleId = vehicleRepository.getActiveVehicleOnce()?.id ?: return@launch
            parkingRepository.clearLocations(vehicleId)
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val container = appContext.appContainer
                    ParkingViewModel(
                        vehicleRepository = container.vehicleRepository,
                        parkingRepository = container.parkingRepository,
                        saveParkingLocation =
                            SaveParkingLocationUseCase(
                                vehicleRepository = container.vehicleRepository,
                                parkingRepository = container.parkingRepository,
                                locationSource = container.locationSource,
                                clock = container.clock,
                                hasLocationPermission = { Permissions.hasLocation(appContext) },
                            ),
                        hasMonitoringPermission = { Permissions.canMonitor(appContext) },
                        hasLocationPermission = { Permissions.hasLocation(appContext) },
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
