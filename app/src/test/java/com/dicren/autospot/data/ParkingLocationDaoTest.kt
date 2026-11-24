package com.dicren.autospot.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParkingLocationDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var vehicles: VehicleDao
    private lateinit var dao: ParkingLocationDao
    private var vehicleId = 0L

    @Before
    fun setUp() =
        runTest {
            db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        AppDatabase::class.java,
                    ).setDriver(AndroidSQLiteDriver())
                    .allowMainThreadQueries()
                    .build()
            vehicles = db.vehicleDao()
            dao = db.parkingLocationDao()
            vehicleId =
                vehicles.insertVehicleAndActivate(
                    VehicleEntity(
                        name = "Car",
                        bluetoothAddress = "AA:BB:CC:DD:EE:01",
                        bluetoothName = "Car multimedia",
                    ),
                )
        }

    @After
    fun tearDown() = db.close()

    private fun location(timestamp: Long) =
        ParkingLocationEntity(
            vehicleId = vehicleId,
            latitude = 1.0,
            longitude = 2.0,
            timestamp = timestamp,
        )

    @Test
    fun `locations are listed newest first`() =
        runTest {
            dao.insertLocation(location(100))
            dao.insertLocation(location(300))
            dao.insertLocation(location(200))

            assertEquals(listOf(300L, 200L, 100L), dao.getLocationsByVehicle(vehicleId).first().map { it.timestamp })
        }

    @Test
    fun `the latest location is the newest one`() =
        runTest {
            dao.insertLocation(location(100))
            dao.insertLocation(location(300))
            dao.insertLocation(location(200))

            assertEquals(300L, dao.getLatestLocationByVehicle(vehicleId).first()?.timestamp)
            assertEquals(300L, dao.getLatestLocationByVehicleOnce(vehicleId)?.timestamp)
        }

    @Test
    fun `an empty history returns null`() =
        runTest {
            assertNull(dao.getLatestLocationByVehicleOnce(vehicleId))
            assertTrue(dao.getLocationsByVehicle(vehicleId).first().isEmpty())
        }

    @Test
    fun `pruning keeps the newest entries`() =
        runTest {
            (1..10).forEach { dao.insertLocation(location(it.toLong())) }

            dao.pruneTo(vehicleId, keep = 3)

            assertEquals(listOf(10L, 9L, 8L), dao.getLocationsByVehicle(vehicleId).first().map { it.timestamp })
        }

    @Test
    fun `inserting prunes in the same transaction`() =
        runTest {
            (1..5).forEach { dao.insertAndPrune(location(it.toLong()), keep = 3) }

            assertEquals(3, dao.getLocationsByVehicle(vehicleId).first().size)
            assertEquals(5L, dao.getLatestLocationByVehicleOnce(vehicleId)?.timestamp)
        }

    @Test
    fun `clearing removes every location of that vehicle`() =
        runTest {
            dao.insertLocation(location(1))
            dao.insertLocation(location(2))

            dao.deleteAllLocationsByVehicle(vehicleId)

            assertTrue(dao.getLocationsByVehicle(vehicleId).first().isEmpty())
        }

    @Test
    fun `deleting one location keeps the rest`() =
        runTest {
            val id = dao.insertLocation(location(1))
            dao.insertLocation(location(2))

            dao.deleteLocation(id)

            assertEquals(1, dao.getLocationsByVehicle(vehicleId).first().size)
        }

    @Test
    fun `history is scoped per vehicle`() =
        runTest {
            val otherId =
                vehicles.insertVehicleAndActivate(
                    VehicleEntity(
                        name = "Other",
                        bluetoothAddress = "AA:BB:CC:DD:EE:02",
                        bluetoothName = "Other car",
                    ),
                )
            dao.insertLocation(location(1))
            dao.insertLocation(
                ParkingLocationEntity(vehicleId = otherId, latitude = 3.0, longitude = 4.0, timestamp = 2),
            )

            assertEquals(1, dao.getLocationsByVehicle(vehicleId).first().size)
            assertEquals(1, dao.getLocationsByVehicle(otherId).first().size)
        }
}
