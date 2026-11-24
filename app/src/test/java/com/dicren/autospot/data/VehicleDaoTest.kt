package com.dicren.autospot.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VehicleDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: VehicleDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    AppDatabase::class.java,
                ).setDriver(AndroidSQLiteDriver())
                .allowMainThreadQueries()
                .build()
        dao = db.vehicleDao()
    }

    @After
    fun tearDown() = db.close()

    private fun vehicle(
        name: String = "Car",
        address: String = "AA:BB:CC:DD:EE:01",
        active: Boolean = false,
    ) = VehicleEntity(
        name = name,
        bluetoothAddress = address,
        bluetoothName = "Car multimedia",
        isActive = active,
    )

    @Test
    fun `a vehicle can be read back`() =
        runTest {
            val id = dao.insertVehicle(vehicle(name = "Mi coche"))

            val stored = dao.getVehicleById(id)

            assertEquals("Mi coche", stored?.name)
            assertEquals("AA:BB:CC:DD:EE:01", stored?.bluetoothAddress)
        }

    @Test
    fun `the same bluetooth address cannot be stored twice`() =
        runTest {
            dao.insertVehicle(vehicle(address = "AA:BB:CC:DD:EE:01"))

            val error =
                runCatching {
                    dao.insertVehicle(vehicle(name = "Other", address = "AA:BB:CC:DD:EE:01"))
                }.exceptionOrNull()

            assertTrue("Expected a constraint failure, got $error", error is SQLiteConstraintException)
        }

    @Test
    fun `adding a vehicle activates it and deactivates the previous one`() =
        runTest {
            val first = dao.insertVehicleAndActivate(vehicle(name = "A", address = "AA:BB:CC:DD:EE:01"))
            val second = dao.insertVehicleAndActivate(vehicle(name = "B", address = "AA:BB:CC:DD:EE:02"))

            assertEquals(second, dao.getActiveVehicleOnce()?.id)
            assertEquals(1, dao.getAllVehiclesOnce().count { it.isActive })
            assertFalse(dao.getVehicleById(first)?.isActive == true)
        }

    @Test
    fun `activateOnly leaves exactly one active vehicle`() =
        runTest {
            val first = dao.insertVehicleAndActivate(vehicle(address = "AA:BB:CC:DD:EE:01"))
            dao.insertVehicleAndActivate(vehicle(address = "AA:BB:CC:DD:EE:02"))

            dao.activateOnly(first)

            assertEquals(first, dao.getActiveVehicleOnce()?.id)
            assertEquals(1, dao.getAllVehiclesOnce().count { it.isActive })
        }

    @Test
    fun `deleting the active vehicle activates another one`() =
        runTest {
            val first = dao.insertVehicleAndActivate(vehicle(name = "A", address = "AA:BB:CC:DD:EE:01"))
            val second = dao.insertVehicleAndActivate(vehicle(name = "B", address = "AA:BB:CC:DD:EE:02"))

            dao.deleteVehicleAndKeepOneActive(second)

            assertEquals(first, dao.getActiveVehicleOnce()?.id)
        }

    @Test
    fun `deleting a non active vehicle keeps the active one`() =
        runTest {
            val first = dao.insertVehicleAndActivate(vehicle(name = "A", address = "AA:BB:CC:DD:EE:01"))
            val second = dao.insertVehicleAndActivate(vehicle(name = "B", address = "AA:BB:CC:DD:EE:02"))

            dao.deleteVehicleAndKeepOneActive(first)

            assertEquals(second, dao.getActiveVehicleOnce()?.id)
        }

    @Test
    fun `deleting the last vehicle leaves no active vehicle`() =
        runTest {
            val only = dao.insertVehicleAndActivate(vehicle())

            dao.deleteVehicleAndKeepOneActive(only)

            assertNull(dao.getActiveVehicleOnce())
            assertFalse(dao.hasActiveVehicle())
        }

    @Test
    fun `deleting a vehicle deletes its parking locations`() =
        runTest {
            val id = dao.insertVehicleAndActivate(vehicle())
            db.parkingLocationDao().insertLocation(
                ParkingLocationEntity(vehicleId = id, latitude = 1.0, longitude = 2.0, timestamp = 0),
            )

            dao.deleteVehicleAndKeepOneActive(id)

            assertTrue(
                db
                    .parkingLocationDao()
                    .getLocationsByVehicle(id)
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `the active vehicle flow reflects changes`() =
        runTest {
            assertNull(dao.getActiveVehicle().first())

            val id = dao.insertVehicleAndActivate(vehicle())

            assertEquals(id, dao.getActiveVehicle().first()?.id)
        }
}
