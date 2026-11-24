package com.dicren.autospot.ui.parking

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.dicren.autospot.core.Clock
import com.dicren.autospot.data.AddVehicleResult
import com.dicren.autospot.data.AppDatabase
import com.dicren.autospot.data.ParkingRepository
import com.dicren.autospot.data.RoomParkingRepository
import com.dicren.autospot.data.RoomVehicleRepository
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.domain.SaveParkingLocationUseCase
import com.dicren.autospot.location.LocationFix
import com.dicren.autospot.location.LocationSource
import com.dicren.autospot.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParkingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeLocationSource(
        var fix: LocationFix?,
    ) : LocationSource {
        override suspend fun currentLocation(): LocationFix? = fix

        override suspend fun lastLocation(): LocationFix? = fix
    }

    private lateinit var db: AppDatabase
    private lateinit var vehicles: RoomVehicleRepository
    private lateinit var parking: RoomParkingRepository
    private lateinit var source: FakeLocationSource
    private var monitoringPermission = true
    private var locationPermission = true
    private var gpsEnabled = true
    private var vehicleCount = 0

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
        parking = RoomParkingRepository(db.parkingLocationDao(), Clock { NOW })
        source = FakeLocationSource(LocationFix(42.0, -5.0, NOW - 1_000, 5f))
    }

    @After
    fun tearDown() = db.close()

    private fun viewModel(): ParkingViewModel =
        ParkingViewModel(
            vehicleRepository = vehicles,
            parkingRepository = parking,
            saveParkingLocation =
                SaveParkingLocationUseCase(
                    vehicleRepository = vehicles,
                    parkingRepository = parking,
                    locationSource = source,
                    clock = Clock { NOW },
                    hasLocationPermission = { locationPermission },
                ),
            hasMonitoringPermission = { monitoringPermission },
            hasLocationPermission = { locationPermission },
            isLocationEnabled = { gpsEnabled },
        )

    private suspend fun addVehicle(name: String = "Car"): Long {
        vehicleCount++
        val address = "AA:BB:CC:DD:EE:${vehicleCount.toString().padStart(2, '0')}"
        return (vehicles.addVehicle(name, address, "Car") as AddVehicleResult.Added).id
    }

    private fun vehicle(name: String) =
        VehicleEntity(
            id = 0,
            name = name,
            bluetoothAddress = "AA:BB:CC:DD:EE:99",
            bluetoothName = "Car",
        )

    @Test
    fun `the active vehicle drives the visible locations`() =
        runTest {
            val id = addVehicle()
            parking.saveLocation(id, 42.0, -5.0)
            val viewModel = viewModel()

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.locations.isEmpty()) state = awaitItem()
                assertEquals(1, state.locations.size)
                assertEquals(id, state.activeVehicle?.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `switching vehicles forgets the selected history item`() =
        runTest {
            val first = addVehicle("A")
            val second = addVehicle("B")
            parking.saveLocation(first, 1.0, 1.0)
            parking.saveLocation(second, 2.0, 2.0)
            val viewModel = viewModel()

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.locations.isEmpty() || state.locations.any { it.vehicleId != second }) {
                    state = awaitItem()
                }
                viewModel.selectLocation(state.locations.first())
                state = awaitItem()
                assertTrue(state.isShowingHistory)

                vehicles.activateVehicle(first)
                while (state.activeVehicle?.id != first || state.selectedLocationId != null) {
                    state = awaitItem()
                }

                assertFalse(state.isShowingHistory)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleting the active vehicle activates another one`() =
        runTest {
            val first = addVehicle("A")
            val second = addVehicle("B")
            val viewModel = viewModel()

            viewModel.deleteVehicle(vehicles.getAllVehiclesOnce().first { it.id == second })

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.activeVehicle?.id != first) state = awaitItem()
                assertEquals(1, state.vehicles.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saving stores a location and reports success`() =
        runTest {
            addVehicle()
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.saveCurrentLocation()
                assertEquals(ParkingEvent.LocationSaved, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.locations.isEmpty()) state = awaitItem()
                assertEquals(1, state.locations.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saving without a vehicle reports it`() =
        runTest {
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.saveCurrentLocation()
                assertEquals(ParkingEvent.NoVehicle, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saving without a fix reports a failure`() =
        runTest {
            addVehicle()
            source.fix = null
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.saveCurrentLocation()
                assertEquals(ParkingEvent.SaveFailed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `adding the same device twice reports a duplicate`() =
        runTest {
            addVehicle("A")
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.addVehicle("B", "AA:BB:CC:DD:EE:01", "Car")
                assertEquals(ParkingEvent.DuplicateVehicle, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `updating to a device used by another vehicle reports the conflict`() =
        runTest {
            addVehicle("A")
            addVehicle("B")
            val other = vehicles.getAllVehiclesOnce().first { it.name == "B" }
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.updateVehicle(other.copy(bluetoothAddress = "AA:BB:CC:DD:EE:01"))
                assertEquals(ParkingEvent.VehicleUpdateConflict, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleting a location removes it and clears the selection`() =
        runTest {
            val id = addVehicle()
            parking.saveLocation(id, 1.0, 1.0)
            parking.saveLocation(id, 2.0, 2.0)
            val viewModel = viewModel()
            val location = parking.latestLocationOnce(id)!!

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.locations.size != 2) state = awaitItem()

                viewModel.selectLocation(location)
                state = awaitItem()
                assertTrue(state.isShowingHistory)

                viewModel.deleteLocation(location)
                while (state.locations.size != 1) state = awaitItem()

                assertNull(state.selectedLocationId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refreshEnvironment picks up permission changes`() =
        runTest {
            addVehicle()
            val viewModel = viewModel()

            viewModel.uiState.test {
                var state = awaitItem()
                while (!state.hasMonitoringPermission) state = awaitItem()
                monitoringPermission = false
                gpsEnabled = false
                viewModel.refreshEnvironment()
                state = awaitItem()
                assertFalse(state.hasMonitoringPermission)
                assertFalse(state.isLocationEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `clearing the history keeps the vehicle`() =
        runTest {
            val id = addVehicle()
            parking.saveLocation(id, 1.0, 1.0)
            val viewModel = viewModel()

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.locations.isEmpty()) state = awaitItem()

                viewModel.clearHistory()
                while (state.locations.isNotEmpty()) state = awaitItem()

                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, vehicles.getAllVehiclesOnce().size)
        }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
