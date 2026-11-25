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
import com.dicren.autospot.domain.LocateSelf
import com.dicren.autospot.domain.LocateSelfUseCase
import com.dicren.autospot.domain.ResolveLocationAddress
import com.dicren.autospot.domain.ResolveLocationAddressUseCase
import com.dicren.autospot.domain.SaveParkingLocation
import com.dicren.autospot.domain.SaveParkingLocationUseCase
import com.dicren.autospot.location.AndroidReverseGeocoder
import com.dicren.autospot.location.FusedLocationSource
import com.dicren.autospot.location.LocationSource
import com.dicren.autospot.location.ReverseGeocoder
import com.dicren.autospot.util.Permissions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Tiny manual service locator. Deliberately not Hilt: there is one activity, two services and
 * one database, so a container with lazy fields is enough and stays trivially fakeable in tests.
 */
interface AppContainer {
    val clock: Clock
    val ioDispatcher: CoroutineDispatcher
    val locationSource: LocationSource
    val vehicleRepository: VehicleRepository
    val parkingRepository: ParkingRepository
    val settingsRepository: SettingsRepository
    val saveParkingLocationUseCase: SaveParkingLocation
    val locateSelf: LocateSelf
    val reverseGeocoder: ReverseGeocoder
    val resolveLocationAddressUseCase: ResolveLocationAddress
}

class DefaultAppContainer(
    context: Context,
    override val clock: Clock = DefaultClock,
    override val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppContainer {
    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(appContext)
    }

    override val locationSource: LocationSource by lazy {
        FusedLocationSource(appContext)
    }

    override val vehicleRepository: VehicleRepository by lazy {
        RoomVehicleRepository(database.vehicleDao())
    }

    override val parkingRepository: ParkingRepository by lazy {
        RoomParkingRepository(database.parkingLocationDao(), clock)
    }

    override val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(appContext.settingsDataStore)
    }

    override val saveParkingLocationUseCase: SaveParkingLocationUseCase by lazy {
        SaveParkingLocationUseCase(
            vehicleRepository = vehicleRepository,
            parkingRepository = parkingRepository,
            locationSource = locationSource,
            clock = clock,
            hasLocationPermission = { Permissions.hasLocation(appContext) },
        )
    }

    override val locateSelf: LocateSelf by lazy {
        LocateSelfUseCase(
            locationSource = locationSource,
            hasLocationPermission = { Permissions.hasLocation(appContext) },
        )
    }

    override val reverseGeocoder: ReverseGeocoder by lazy {
        AndroidReverseGeocoder(appContext, ioDispatcher)
    }

    override val resolveLocationAddressUseCase: ResolveLocationAddressUseCase by lazy {
        ResolveLocationAddressUseCase(
            parkingRepository = parkingRepository,
            reverseGeocoder = reverseGeocoder,
        )
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as AutoSpotApp).container
