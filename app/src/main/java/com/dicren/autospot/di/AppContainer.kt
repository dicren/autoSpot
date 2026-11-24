package com.dicren.autospot.di

import android.content.Context
import com.dicren.autospot.AutoSpotApp
import com.dicren.autospot.core.Clock
import com.dicren.autospot.core.DefaultClock
import com.dicren.autospot.data.AppDatabase
import com.dicren.autospot.data.DataStoreSettingsRepository
import com.dicren.autospot.data.ParkingRepository
import com.dicren.autospot.data.RoomParkingRepository
import com.dicren.autospot.data.RoomVehicleRepository
import com.dicren.autospot.data.SettingsRepository
import com.dicren.autospot.data.VehicleRepository
import com.dicren.autospot.data.settingsDataStore

/**
 * Tiny manual service locator. Deliberately not Hilt: there is one activity, two services and
 * one database, so a container with lazy fields is enough and stays trivially fakeable in tests.
 */
interface AppContainer {
    val clock: Clock
    val vehicleRepository: VehicleRepository
    val parkingRepository: ParkingRepository
    val settingsRepository: SettingsRepository
}

class DefaultAppContainer(
    context: Context,
    override val clock: Clock = DefaultClock,
) : AppContainer {

    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy { AppDatabase.getDatabase(appContext) }

    override val vehicleRepository: VehicleRepository by lazy {
        RoomVehicleRepository(database.vehicleDao())
    }

    override val parkingRepository: ParkingRepository by lazy {
        RoomParkingRepository(database.parkingLocationDao(), clock)
    }

    override val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(appContext.settingsDataStore)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as AutoSpotApp).container
