package com.dicren.autospot.data

import android.content.Context
import com.dicren.autospot.di.appContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Temporary facade kept while the UI migrates to the ViewModel. It delegates to the repositories
 * in [com.dicren.autospot.di.AppContainer]; new code should use those directly.
 */
class LocationRepository(context: Context) {

    private val vehicleRepository = context.appContainer.vehicleRepository
    private val parkingRepository = context.appContainer.parkingRepository
    private val settingsRepository = context.appContainer.settingsRepository

    // Onboarding
    val isOnboardingCompleted: Flow<Boolean> = settingsRepository.isOnboardingCompleted

    suspend fun setOnboardingCompleted() = settingsRepository.setOnboardingCompleted()

    // Vehicles
    val allVehicles: Flow<List<VehicleEntity>> = vehicleRepository.vehicles
    val activeVehicle: Flow<VehicleEntity?> = vehicleRepository.activeVehicle

    // Parking locations for the active vehicle
    @OptIn(ExperimentalCoroutinesApi::class)
    val allParkingLocations: Flow<List<ParkingLocationEntity>> = activeVehicle.flatMapLatest { vehicle ->
        vehicle?.let { parkingRepository.locations(it.id) } ?: flowOf(emptyList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestParkingLocation: Flow<ParkingLocationEntity?> = activeVehicle.flatMapLatest { vehicle ->
        vehicle?.let { parkingRepository.latestLocation(it.id) } ?: flowOf(null)
    }

    suspend fun saveLocation(vehicleId: Long, latitude: Double, longitude: Double) {
        parkingRepository.saveLocation(vehicleId, latitude, longitude)
    }

    suspend fun deleteLocation(id: Long) = parkingRepository.deleteLocation(id)

    suspend fun clearAllLocations(vehicleId: Long) = parkingRepository.clearLocations(vehicleId)

    suspend fun addVehicle(name: String, bluetoothAddress: String, bluetoothName: String): Long =
        when (val result = vehicleRepository.addVehicle(name, bluetoothAddress, bluetoothName)) {
            is AddVehicleResult.Added -> result.id
            is AddVehicleResult.Duplicate -> result.existingId
        }

    suspend fun updateVehicle(vehicle: VehicleEntity) {
        vehicleRepository.updateVehicle(vehicle)
    }

    suspend fun deleteVehicle(id: Long) = vehicleRepository.deleteVehicle(id)

    suspend fun setActiveVehicle(id: Long) = vehicleRepository.activateVehicle(id)

    suspend fun getActiveVehicleSync(): VehicleEntity? = vehicleRepository.getActiveVehicleOnce()

    suspend fun getVehicleByBluetoothAddress(address: String): VehicleEntity? =
        vehicleRepository.getVehicleByBluetoothAddressOnce(address)

    suspend fun getAllVehiclesSync(): List<VehicleEntity> = vehicleRepository.getAllVehiclesOnce()

    suspend fun getLatestLocationByVehicleSync(vehicleId: Long): ParkingLocationEntity? =
        parkingRepository.latestLocationOnce(vehicleId)
}
