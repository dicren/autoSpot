package com.dicren.autospot.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class LocationRepository(context: Context) {

    private val dataStore = context.dataStore
    private val database = AppDatabase.getDatabase(context)
    private val parkingLocationDao = database.parkingLocationDao()
    private val vehicleDao = database.vehicleDao()

    // Onboarding
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")

    val isOnboardingCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[onboardingCompletedKey] ?: false
    }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { preferences ->
            preferences[onboardingCompletedKey] = true
        }
    }

    // Vehicles
    val allVehicles: Flow<List<VehicleEntity>> = vehicleDao.getAllVehicles()
    val activeVehicle: Flow<VehicleEntity?> = vehicleDao.getActiveVehicle()

    // Parking locations for active vehicle
    @OptIn(ExperimentalCoroutinesApi::class)
    val allParkingLocations: Flow<List<ParkingLocationEntity>> = activeVehicle.flatMapLatest { vehicle ->
        vehicle?.let { parkingLocationDao.getLocationsByVehicle(it.id) } ?: flowOf(emptyList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestParkingLocation: Flow<ParkingLocationEntity?> = activeVehicle.flatMapLatest { vehicle ->
        vehicle?.let { parkingLocationDao.getLatestLocationByVehicle(it.id) } ?: flowOf(null)
    }

    suspend fun saveLocation(vehicleId: Long, latitude: Double, longitude: Double) {
        val location = ParkingLocationEntity(
            vehicleId = vehicleId,
            latitude = latitude,
            longitude = longitude,
            timestamp = System.currentTimeMillis()
        )
        parkingLocationDao.insertLocation(location)
    }

    suspend fun deleteLocation(id: Long) {
        parkingLocationDao.deleteLocation(id)
    }

    suspend fun clearAllLocations(vehicleId: Long) {
        parkingLocationDao.deleteAllLocationsByVehicle(vehicleId)
    }

    suspend fun addVehicle(name: String, bluetoothAddress: String, bluetoothName: String): Long {
        val vehicle = VehicleEntity(
            name = name,
            bluetoothAddress = bluetoothAddress,
            bluetoothName = bluetoothName,
            isActive = false
        )
        return vehicleDao.insertVehicle(vehicle)
    }

    suspend fun updateVehicle(vehicle: VehicleEntity) {
        vehicleDao.updateVehicle(vehicle)
    }

    suspend fun deleteVehicle(id: Long) {
        vehicleDao.deleteVehicle(id)
    }

    suspend fun setActiveVehicle(id: Long) {
        vehicleDao.deactivateAllVehicles()
        vehicleDao.activateVehicle(id)
    }

    suspend fun deactivateAllVehicles() {
        vehicleDao.deactivateAllVehicles()
    }

    suspend fun getActiveVehicleSync(): VehicleEntity? {
        return vehicleDao.getActiveVehicleSync()
    }

    suspend fun getVehicleByBluetoothAddress(address: String): VehicleEntity? {
        return vehicleDao.getVehicleByBluetoothAddress(address)
    }

    suspend fun getAllVehiclesSync(): List<VehicleEntity> {
        return vehicleDao.getAllVehiclesSync()
    }

    suspend fun getLatestLocationByVehicleSync(vehicleId: Long): ParkingLocationEntity? {
        return parkingLocationDao.getLatestLocationByVehicleSync(vehicleId)
    }
}
