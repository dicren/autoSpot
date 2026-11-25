package com.dicren.autospot.util

import com.dicren.autospot.core.Clock
import com.dicren.autospot.data.AddVehicleResult
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.ParkingRepository
import com.dicren.autospot.data.SettingsRepository
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.data.VehicleRepository
import com.dicren.autospot.di.AppContainer
import com.dicren.autospot.domain.SaveParkingLocation
import com.dicren.autospot.domain.SaveParkingLocationUseCase
import com.dicren.autospot.location.LocationFix
import com.dicren.autospot.location.LocationSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.yield

/**
 * Hand-written test doubles, shared by the tests that need an [AppContainer].
 *
 * [FakeParkingRepository.latestLocationOnce] suspends on purpose: production reads from Room,
 * which never answers synchronously. A fake that returned immediately would hide ordering bugs
 * (the headless activities finish after a real suspension).
 */
class FakeVehicleRepository(
    initial: List<VehicleEntity> = emptyList(),
) : VehicleRepository {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    /** Every call to [getActiveVehicleOnce] is preceded by a suspension, like a Room query. */
    private suspend fun await() = yield()

    override val vehicles: Flow<List<VehicleEntity>> = state
    override val activeVehicle: Flow<VehicleEntity?> = state.map { all -> all.firstOrNull { it.isActive } }

    override suspend fun addVehicle(
        name: String,
        bluetoothAddress: String,
        bluetoothName: String,
    ): AddVehicleResult {
        await()
        val existing = state.value.firstOrNull { it.bluetoothAddress == bluetoothAddress }
        if (existing != null) return AddVehicleResult.Duplicate(existing.id)

        val id = nextId++
        state.value =
            state.value.map { it.copy(isActive = false) } +
            VehicleEntity(id, name, bluetoothAddress, bluetoothName, isActive = true)
        return AddVehicleResult.Added(id)
    }

    override suspend fun updateVehicle(vehicle: VehicleEntity): Boolean {
        await()
        state.value = state.value.map { if (it.id == vehicle.id) vehicle else it }
        return true
    }

    override suspend fun deleteVehicle(id: Long) {
        await()
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun activateVehicle(id: Long) {
        await()
        state.value = state.value.map { it.copy(isActive = it.id == id) }
    }

    override suspend fun getActiveVehicleOnce(): VehicleEntity? {
        await()
        return state.value.firstOrNull { it.isActive }
    }

    override suspend fun getVehicleByIdOnce(id: Long): VehicleEntity? {
        await()
        return state.value.firstOrNull { it.id == id }
    }

    override suspend fun getVehicleByBluetoothAddressOnce(address: String): VehicleEntity? {
        await()
        return state.value.firstOrNull { it.bluetoothAddress == address }
    }

    override suspend fun getAllVehiclesOnce(): List<VehicleEntity> {
        await()
        return state.value
    }
}

class FakeParkingRepository(
    private val history: MutableMap<Long, List<ParkingLocationEntity>> = mutableMapOf(),
) : ParkingRepository {
    private var nextId = 1L

    override fun locations(vehicleId: Long): Flow<List<ParkingLocationEntity>> = flowOf(history[vehicleId].orEmpty())

    override suspend fun saveLocation(
        vehicleId: Long,
        latitude: Double,
        longitude: Double,
    ): Long {
        yield()
        val id = nextId++
        history[vehicleId] =
            listOf(
                ParkingLocationEntity(
                    id = id,
                    vehicleId = vehicleId,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = id,
                ),
            ) + history[vehicleId].orEmpty()
        return id
    }

    override suspend fun deleteLocation(id: Long) {
        yield()
        history.replaceAll { _, locations -> locations.filterNot { it.id == id } }
    }

    override suspend fun clearLocations(vehicleId: Long) {
        yield()
        history.remove(vehicleId)
    }

    override suspend fun latestLocationOnce(vehicleId: Long): ParkingLocationEntity? {
        yield()
        return history[vehicleId]?.firstOrNull()
    }
}

class FakeSettingsRepository(
    onboardingCompleted: Boolean = true,
    backgroundRationaleShown: Boolean = false,
) : SettingsRepository {
    private val completed = MutableStateFlow(onboardingCompleted)
    private val rationaleShown = MutableStateFlow(backgroundRationaleShown)
    override val isOnboardingCompleted: Flow<Boolean> = completed
    override val isBackgroundLocationRationaleShown: Flow<Boolean> = rationaleShown

    override suspend fun setOnboardingCompleted() {
        completed.value = true
    }

    override suspend fun setBackgroundLocationRationaleShown() {
        rationaleShown.value = true
    }
}

class FakeLocationSource(
    private val fix: LocationFix? = null,
) : LocationSource {
    override suspend fun currentLocation(): LocationFix? = fix

    override suspend fun lastLocation(): LocationFix? = fix
}

class TestAppContainer(
    override val clock: Clock = Clock { 0L },
    override val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    override val locationSource: LocationSource = FakeLocationSource(),
    override val vehicleRepository: VehicleRepository = FakeVehicleRepository(),
    override val parkingRepository: ParkingRepository = FakeParkingRepository(),
    override val settingsRepository: SettingsRepository = FakeSettingsRepository(),
) : AppContainer {
    override val saveParkingLocationUseCase: SaveParkingLocation =
        SaveParkingLocationUseCase(
            vehicleRepository = vehicleRepository,
            parkingRepository = parkingRepository,
            locationSource = locationSource,
            clock = clock,
            hasLocationPermission = { true },
        )
}
