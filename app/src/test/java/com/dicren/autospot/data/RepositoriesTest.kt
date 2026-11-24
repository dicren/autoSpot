package com.dicren.autospot.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dicren.autospot.core.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoriesTest {
    private lateinit var db: AppDatabase
    private lateinit var vehicles: RoomVehicleRepository
    private lateinit var parking: RoomParkingRepository

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        vehicles = RoomVehicleRepository(db.vehicleDao())
        parking = RoomParkingRepository(db.parkingLocationDao(), Clock { FIXED_NOW })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `adding a vehicle makes it active`() =
        runTest {
            val result = vehicles.addVehicle("Mi coche", "AA:BB:CC:DD:EE:01", "Car")

            assertTrue(result is AddVehicleResult.Added)
            assertEquals(
                (result as AddVehicleResult.Added).id,
                vehicles.getActiveVehicleOnce()?.id,
            )
        }

    @Test
    fun `adding the same bluetooth address twice is reported as duplicate`() =
        runTest {
            val first = vehicles.addVehicle("A", "AA:BB:CC:DD:EE:01", "Car")

            val second = vehicles.addVehicle("B", "AA:BB:CC:DD:EE:01", "Car")

            assertEquals(AddVehicleResult.Duplicate((first as AddVehicleResult.Added).id), second)
            assertEquals(1, vehicles.getAllVehiclesOnce().size)
        }

    @Test
    fun `updating a vehicle to an address owned by another one fails`() =
        runTest {
            val first = vehicles.addVehicle("A", "AA:BB:CC:DD:EE:01", "Car")
            vehicles.addVehicle("B", "AA:BB:CC:DD:EE:02", "Car")
            val second = vehicles.getAllVehiclesOnce().first { it.name == "B" }

            val updated = vehicles.updateVehicle(second.copy(bluetoothAddress = "AA:BB:CC:DD:EE:01"))

            assertFalse(updated)
            assertEquals(
                "AA:BB:CC:DD:EE:02",
                vehicles.getAllVehiclesOnce().first { it.id == second.id }.bluetoothAddress,
            )
            assertTrue(first is AddVehicleResult.Added)
        }

    @Test
    fun `deleting the active vehicle activates another one`() =
        runTest {
            val first = vehicles.addVehicle("A", "AA:BB:CC:DD:EE:01", "Car") as AddVehicleResult.Added
            val second = vehicles.addVehicle("B", "AA:BB:CC:DD:EE:02", "Car") as AddVehicleResult.Added

            vehicles.deleteVehicle(second.id)

            assertEquals(first.id, vehicles.getActiveVehicleOnce()?.id)
        }

    @Test
    fun `saved locations use the injected clock`() =
        runTest {
            val vehicle = vehicles.addVehicle("A", "AA:BB:CC:DD:EE:01", "Car") as AddVehicleResult.Added

            parking.saveLocation(vehicle.id, 42.0, -5.0)

            val saved = parking.latestLocationOnce(vehicle.id)
            assertEquals(FIXED_NOW, saved?.timestamp)
            assertEquals(42.0, saved?.latitude ?: 0.0, 0.0)
        }

    @Test
    fun `saving more than the cap keeps only the newest locations`() =
        runTest {
            val vehicle = vehicles.addVehicle("A", "AA:BB:CC:DD:EE:01", "Car") as AddVehicleResult.Added

            repeat(ParkingRepository.MAX_LOCATIONS_PER_VEHICLE + 5) {
                parking.saveLocation(vehicle.id, 1.0, 2.0)
            }

            val count =
                db
                    .parkingLocationDao()
                    .getLocationsByVehicle(vehicle.id)
                    .first()
                    .size
            assertEquals(ParkingRepository.MAX_LOCATIONS_PER_VEHICLE, count)
        }

    private companion object {
        const val FIXED_NOW = 1_700_000_000_000L
    }
}
