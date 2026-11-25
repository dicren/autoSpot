package com.dicren.autospot.service

import android.Manifest
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.dicren.autospot.AutoSpotApp
import com.dicren.autospot.core.Clock
import com.dicren.autospot.data.AddVehicleResult
import com.dicren.autospot.data.AppDatabase
import com.dicren.autospot.data.ParkingRepository
import com.dicren.autospot.data.RoomParkingRepository
import com.dicren.autospot.data.RoomVehicleRepository
import com.dicren.autospot.data.SettingsRepository
import com.dicren.autospot.data.VehicleRepository
import com.dicren.autospot.di.AppContainer
import com.dicren.autospot.domain.ResolveLocationAddress
import com.dicren.autospot.domain.ResolveLocationAddressUseCase
import com.dicren.autospot.domain.SaveParkingLocation
import com.dicren.autospot.domain.SaveParkingLocationUseCase
import com.dicren.autospot.domain.SaveResult
import com.dicren.autospot.location.LocationFix
import com.dicren.autospot.location.LocationSource
import com.dicren.autospot.location.ReverseGeocoder
import com.dicren.autospot.util.FakeReverseGeocoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationSaveServiceTest {
    private class FakeLocationSource(
        var fix: LocationFix?,
    ) : LocationSource {
        override suspend fun currentLocation(): LocationFix? = fix

        override suspend fun lastLocation(): LocationFix? = null
    }

    private class TestSettingsRepository : SettingsRepository {
        override val isOnboardingCompleted: Flow<Boolean> = flowOf(true)
        override val isBackgroundLocationRationaleShown: Flow<Boolean> = flowOf(true)

        override suspend fun setOnboardingCompleted() = Unit

        override suspend fun setBackgroundLocationRationaleShown() = Unit
    }

    private class TestContainer(
        override val clock: Clock,
        override val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        override val locationSource: LocationSource,
        override val vehicleRepository: VehicleRepository,
        override val parkingRepository: ParkingRepository,
        override val saveParkingLocationUseCase: SaveParkingLocation =
            SaveParkingLocationUseCase(
                vehicleRepository = vehicleRepository,
                parkingRepository = parkingRepository,
                locationSource = locationSource,
                clock = clock,
                hasLocationPermission = { true },
            ),
    ) : AppContainer {
        override val settingsRepository: SettingsRepository = TestSettingsRepository()

        override val reverseGeocoder: ReverseGeocoder = FakeReverseGeocoder()

        override val resolveLocationAddressUseCase: ResolveLocationAddress =
            ResolveLocationAddressUseCase(
                parkingRepository = parkingRepository,
                reverseGeocoder = reverseGeocoder,
            )
    }

    private class ThrowingSaveParkingLocation : SaveParkingLocation {
        override suspend fun save(vehicleId: Long?): SaveResult = throw IllegalStateException("database closed")
    }

    private lateinit var app: AutoSpotApp
    private lateinit var db: AppDatabase
    private lateinit var vehicles: RoomVehicleRepository
    private lateinit var parking: RoomParkingRepository
    private val source =
        FakeLocationSource(
            LocationFix(latitude = 42.0, longitude = -5.0, timeMillis = NOW - 1_000, accuracyMeters = 5f),
        )
    private val clock = Clock { NOW }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db =
            Room
                .inMemoryDatabaseBuilder(app, AppDatabase::class.java)
                .setDriver(AndroidSQLiteDriver())
                .allowMainThreadQueries()
                .build()
        vehicles = RoomVehicleRepository(db.vehicleDao())
        parking = RoomParkingRepository(db.parkingLocationDao(), clock)
        app.containerOverride =
            TestContainer(
                clock = clock,
                locationSource = source,
                vehicleRepository = vehicles,
                parkingRepository = parking,
            )
        shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    @After
    fun tearDown() {
        app.containerOverride = null
        db.close()
    }

    private fun addVehicle(): Long =
        runBlocking {
            (vehicles.addVehicle("Car", "AA:BB:CC:DD:EE:01", "Car") as AddVehicleResult.Added).id
        }

    private fun startService(): ServiceController<LocationSaveService> {
        val controller = Robolectric.buildService(LocationSaveService::class.java)
        controller.create()
        controller.startCommand(0, 1)
        return controller
    }

    @Test
    fun `saves the location for the active vehicle`() {
        val vehicleId = addVehicle()

        val controller = startService()

        val saved =
            runBlocking {
                withTimeout(5_000) {
                    while (parking.latestLocationOnce(vehicleId) == null) delay(20)
                    parking.latestLocationOnce(vehicleId)
                }
            }

        assertNotNull(saved)
        assertEquals(NOW, saved?.timestamp)
        assertEquals(42.0, saved?.latitude ?: 0.0, 0.0)

        controller.destroy()
    }

    @Test
    fun `without a fix nothing is saved`() {
        source.fix = null
        val vehicleId = addVehicle()

        val controller = startService()
        runBlocking { delay(1_000) }

        val saved = runBlocking { parking.latestLocationOnce(vehicleId) }
        assertNull(saved)

        controller.destroy()
    }

    @Test
    fun `without the location permission it enters the foreground before stopping`() {
        val vehicleId = addVehicle()
        shadowOf(app).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        val controller = startService()

        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        // Stopping without entering the foreground first makes Android kill the process.
        assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
        assertNull(runBlocking { parking.latestLocationOnce(vehicleId) })

        controller.destroy()
    }

    @Test
    fun `an unexpected save failure still stops the service`() {
        addVehicle()
        app.containerOverride =
            TestContainer(
                clock = clock,
                locationSource = source,
                vehicleRepository = vehicles,
                parkingRepository = parking,
                saveParkingLocationUseCase = ThrowingSaveParkingLocation(),
            )

        val controller = startService()

        runBlocking {
            withTimeout(5_000) {
                while (!shadowOf(controller.get()).isStoppedBySelf) delay(20)
            }
        }

        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        controller.destroy()
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
