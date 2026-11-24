package com.dicren.autospot.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dicren.autospot.core.Clock
import com.dicren.autospot.data.AddVehicleResult
import com.dicren.autospot.data.AppDatabase
import com.dicren.autospot.data.ParkingRepository
import com.dicren.autospot.data.RoomParkingRepository
import com.dicren.autospot.data.RoomVehicleRepository
import com.dicren.autospot.data.VehicleRepository
import com.dicren.autospot.location.LocationFix
import com.dicren.autospot.location.LocationSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveParkingLocationUseCaseTest {

    private class FakeClock(var now: Long) : Clock {
        override fun nowMillis(): Long = now
    }

    private class FakeLocationSource : LocationSource {
        var current: LocationFix? = null
        var last: LocationFix? = null

        override suspend fun currentLocation(): LocationFix? = current
        override suspend fun lastLocation(): LocationFix? = last
    }

    private class FailingParkingRepository(
        private val delegate: ParkingRepository,
    ) : ParkingRepository by delegate {
        override suspend fun saveLocation(vehicleId: Long, latitude: Double, longitude: Double): Long =
            throw IllegalStateException("disk full")
    }

    private val clock = FakeClock(NOW)
    private lateinit var db: AppDatabase
    private lateinit var vehicles: VehicleRepository
    private lateinit var parking: ParkingRepository
    private lateinit var source: FakeLocationSource
    private var permission = true

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        vehicles = RoomVehicleRepository(db.vehicleDao())
        parking = RoomParkingRepository(db.parkingLocationDao(), clock)
        source = FakeLocationSource()
    }

    @After
    fun tearDown() = db.close()

    private fun useCase(repository: ParkingRepository = parking) = SaveParkingLocationUseCase(
        vehicleRepository = vehicles,
        parkingRepository = repository,
        locationSource = source,
        clock = clock,
        hasLocationPermission = { permission },
        maxFixAgeMillis = 5 * 60_000L,
    )

    private var vehicleCount = 0

    private suspend fun addVehicle(name: String = "Car"): Long {
        vehicleCount++
        val address = "AA:BB:CC:DD:EE:${vehicleCount.toString().padStart(2, '0')}"
        return (vehicles.addVehicle(name, address, "Car") as AddVehicleResult.Added).id
    }

    private fun fix(
        time: Long = NOW - 1_000L,
        latitude: Double = 42.0,
        longitude: Double = -5.0,
    ) = LocationFix(latitude, longitude, time, accuracyMeters = 5f)

    @Test
    fun `without permission nothing is saved`() = runTest {
        val id = addVehicle()
        permission = false
        source.current = fix()

        val result = useCase().save(id)

        assertEquals(SaveResult.NoPermission, result)
        assertTrue(parking.latestLocationOnce(id) == null)
    }

    @Test
    fun `without vehicles there is nothing to save to`() = runTest {
        source.current = fix()

        assertEquals(SaveResult.NoVehicle, useCase().save(null))
    }

    @Test
    fun `a specific vehicle that no longer exists is not replaced by the active one`() = runTest {
        addVehicle()
        source.current = fix()

        assertEquals(SaveResult.NoVehicle, useCase().save(999L))
    }

    @Test
    fun `a fresh fix is saved for the requested vehicle`() = runTest {
        val id = addVehicle()
        source.current = fix()

        val result = useCase().save(id)

        assertTrue(result is SaveResult.Saved)
        assertEquals(id, (result as SaveResult.Saved).vehicleId)
        assertEquals(NOW, parking.latestLocationOnce(id)?.timestamp)
        assertEquals(42.0, parking.latestLocationOnce(id)?.latitude ?: 0.0, 0.0)
    }

    @Test
    fun `without a vehicle id the active vehicle is used`() = runTest {
        val first = addVehicle("A")
        val second = addVehicle("B")
        source.current = fix()

        val result = useCase().save(null)

        assertEquals(second, (result as SaveResult.Saved).vehicleId)
        assertTrue(parking.latestLocationOnce(first) == null)
        assertTrue(parking.latestLocationOnce(second) != null)
    }

    @Test
    fun `a stale current fix falls back to a fresh last known fix`() = runTest {
        val id = addVehicle()
        source.current = fix(time = NOW - 10 * 60_000L)
        source.last = fix(time = NOW - 30_000L, latitude = 43.0)

        val result = useCase().save(id)

        assertEquals(43.0, (result as SaveResult.Saved).latitude, 0.0)
    }

    @Test
    fun `no current fix uses the last known one`() = runTest {
        val id = addVehicle()
        source.last = fix(latitude = 44.0)

        val result = useCase().save(id)

        assertEquals(44.0, (result as SaveResult.Saved).latitude, 0.0)
    }

    @Test
    fun `without a usable fix nothing is saved`() = runTest {
        val id = addVehicle()
        source.current = fix(time = NOW - 10 * 60_000L)
        source.last = fix(time = NOW - 20 * 60_000L)

        assertEquals(SaveResult.NoFix, useCase().save(id))
        assertTrue(parking.latestLocationOnce(id) == null)
    }

    @Test
    fun `a fix without a timestamp is accepted`() = runTest {
        val id = addVehicle()
        source.current = fix(time = 0L)

        assertTrue(useCase().save(id) is SaveResult.Saved)
    }

    @Test
    fun `a repository failure is reported as an error`() = runTest {
        val id = addVehicle()
        source.current = fix()

        val result = useCase(FailingParkingRepository(parking)).save(id)

        assertTrue(result is SaveResult.Error)
        assertTrue(parking.latestLocationOnce(id) == null)
    }

    @Test
    fun `the newest saved location is the one just stored`() = runTest {
        val id = addVehicle()
        source.current = fix(latitude = 10.0)
        useCase().save(id)

        clock.now = NOW + 60_000
        source.current = fix(latitude = 11.0)
        useCase().save(id)

        val locations = db.parkingLocationDao().getLocationsByVehicle(id).first()
        assertEquals(2, locations.size)
        assertEquals(11.0, locations.first().latitude, 0.0)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
