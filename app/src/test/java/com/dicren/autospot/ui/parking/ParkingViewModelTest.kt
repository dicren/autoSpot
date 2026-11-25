package com.dicren.autospot.ui.parking

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.dicren.autospot.core.Clock
import com.dicren.autospot.data.AddVehicleResult
import com.dicren.autospot.data.AppDatabase
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.ParkingRepository
import com.dicren.autospot.data.RoomParkingRepository
import com.dicren.autospot.data.RoomVehicleRepository
import com.dicren.autospot.data.SettingsRepository
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.data.VehicleRepository
import com.dicren.autospot.domain.LocateSelf
import com.dicren.autospot.domain.LocateSelfUseCase
import com.dicren.autospot.domain.ResolveLocationAddressUseCase
import com.dicren.autospot.domain.SaveParkingLocation
import com.dicren.autospot.domain.SaveParkingLocationUseCase
import com.dicren.autospot.domain.SaveResult
import com.dicren.autospot.location.AddressLookup
import com.dicren.autospot.location.LocationFix
import com.dicren.autospot.location.LocationSource
import com.dicren.autospot.location.ReverseGeocoder
import com.dicren.autospot.util.FakeParkingRepository
import com.dicren.autospot.util.FakeReverseGeocoder
import com.dicren.autospot.util.FakeSettingsRepository
import com.dicren.autospot.util.FakeVehicleRepository
import com.dicren.autospot.util.LocationAccuracy
import com.dicren.autospot.util.MainDispatcherRule
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private var accuracy = LocationAccuracy.PRECISE
    private var backgroundLocation = true
    private var gpsEnabled = true
    private var vehicleCount = 0

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
        vehicles = RoomVehicleRepository(db.vehicleDao())
        parking = RoomParkingRepository(db.parkingLocationDao(), Clock { NOW })
        source = FakeLocationSource(LocationFix(42.0, -5.0, NOW - 1_000, 5f))
    }

    @After
    fun tearDown() = db.close()

    private fun viewModel(
        settings: SettingsRepository = FakeSettingsRepository(),
        vehicleRepository: VehicleRepository = vehicles,
        parkingRepository: ParkingRepository = parking,
        saveParkingLocation: SaveParkingLocation? = null,
        reverseGeocoder: ReverseGeocoder = FakeReverseGeocoder(),
        locateSelf: LocateSelf = LocateSelfUseCase(locationSource = source, hasLocationPermission = { true }),
    ): ParkingViewModel =
        ParkingViewModel(
            vehicleRepository = vehicleRepository,
            parkingRepository = parkingRepository,
            settingsRepository = settings,
            saveParkingLocation =
                saveParkingLocation ?: SaveParkingLocationUseCase(
                    vehicleRepository = vehicleRepository,
                    parkingRepository = parkingRepository,
                    locationSource = source,
                    clock = Clock { NOW },
                    hasLocationPermission = { accuracy == LocationAccuracy.PRECISE },
                ),
            resolveLocationAddress =
                ResolveLocationAddressUseCase(
                    parkingRepository = parkingRepository,
                    reverseGeocoder = reverseGeocoder,
                ),
            locateSelf = locateSelf,
            hasMonitoringPermission = { monitoringPermission },
            locationAccuracy = { accuracy },
            hasBackgroundLocationPermission = { backgroundLocation },
            isLocationEnabled = { gpsEnabled },
        )

    private class FailingVehicleRepository(
        private val delegate: VehicleRepository,
    ) : VehicleRepository by delegate {
        override suspend fun addVehicle(
            name: String,
            bluetoothAddress: String,
            bluetoothName: String,
        ): AddVehicleResult = throw IllegalStateException("disk full")
    }

    private class FailingParkingRepository(
        private val delegate: ParkingRepository,
    ) : ParkingRepository by delegate {
        override suspend fun deleteLocation(id: Long) = throw IllegalStateException("disk full")
    }

    /** Emits the new vehicle's history only after a delay, like a slow Room query. */
    private class ThrowingSaveParkingLocation : SaveParkingLocation {
        override suspend fun save(vehicleId: Long?): SaveResult = throw IllegalStateException("database closed")
    }

    /** Answers [result] and signals every call, so a test can wait for the lookup to happen. */
    private class ScriptedReverseGeocoder(
        initial: AddressLookup,
    ) : ReverseGeocoder {
        @Volatile
        var result: AddressLookup = initial

        val calls = Channel<Unit>(Channel.UNLIMITED)

        override suspend fun lookup(
            latitude: Double,
            longitude: Double,
        ): AddressLookup {
            val answer = result
            calls.trySend(Unit)
            return answer
        }
    }

    private class SlowSwitchParkingRepository(
        private val delegate: ParkingRepository,
        private val slowVehicleId: Long,
    ) : ParkingRepository by delegate {
        override fun locations(vehicleId: Long): Flow<List<ParkingLocationEntity>> =
            if (vehicleId == slowVehicleId) {
                flow {
                    delay(1_000)
                    emit(emptyList())
                }
            } else {
                delegate.locations(vehicleId)
            }
    }

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
    fun `the first real emission marks the state as ready`() =
        runTest {
            val viewModel = viewModel()

            viewModel.uiState.test {
                var state = awaitItem()
                while (!state.isReady) state = awaitItem()
                assertTrue(state.isReady)
                cancelAndIgnoreRemainingEvents()
            }
        }

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
                while (state.selectedLocationId == null) state = awaitItem()
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
    fun `switching vehicles never shows the previous history`() =
        runTest {
            val first =
                VehicleEntity(
                    id = 1,
                    name = "A",
                    bluetoothAddress = "AA:BB:CC:DD:EE:01",
                    bluetoothName = "A",
                    isActive = true,
                )
            val second =
                VehicleEntity(
                    id = 2,
                    name = "B",
                    bluetoothAddress = "AA:BB:CC:DD:EE:02",
                    bluetoothName = "B",
                )
            val parking =
                FakeParkingRepository(
                    mutableMapOf(
                        first.id to listOf(ParkingLocationEntity(10, first.id, 1.0, 1.0, 1L)),
                    ),
                )
            val viewModel =
                viewModel(
                    vehicleRepository = FakeVehicleRepository(listOf(first, second)),
                    parkingRepository = SlowSwitchParkingRepository(parking, slowVehicleId = second.id),
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.locations.isEmpty()) state = awaitItem()
                assertEquals(first.id, state.activeVehicle?.id)

                viewModel.activateVehicle(second)

                // The second vehicle's history is still delayed; the first one's must not leak.
                state = awaitItem()
                while (state.activeVehicle?.id != second.id) state = awaitItem()
                assertTrue(state.locations.isEmpty())

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
    fun `a stored spot gets its address resolved in the background`() =
        runTest {
            val id = addVehicle()
            parking.saveLocation(id, 42.0, -5.0)
            val viewModel =
                viewModel(
                    reverseGeocoder =
                        FakeReverseGeocoder(AddressLookup.Found("Calle Mayor 12", "Getafe")),
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.latestLocation?.street == null) state = awaitItem()

                // The coordinates stay intact: the address is an extra, not a replacement.
                assertEquals(42.0, state.latestLocation?.latitude ?: 0.0, 0.0)
                assertEquals("Calle Mayor 12", state.latestLocation?.street)
                assertEquals("Getafe", state.latestLocation?.locality)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an address that could not be resolved is retried when the app resumes`() =
        runTest {
            val id = addVehicle()
            parking.saveLocation(id, 42.0, -5.0)
            val geocoder = ScriptedReverseGeocoder(AddressLookup.Unavailable)
            val viewModel = viewModel(reverseGeocoder = geocoder)

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.latestLocation == null) state = awaitItem()

                // The first pass ran and the geocoder had no network.
                geocoder.calls.receive()
                assertNull(state.latestLocation?.street)

                geocoder.result = AddressLookup.Found("Calle Mayor 12", "Getafe")
                viewModel.refreshEnvironment()

                while (state.latestLocation?.street == null) state = awaitItem()
                assertEquals("Calle Mayor 12", state.latestLocation?.street)
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
    fun `an unexpected save failure reports a failure instead of crashing`() =
        runTest {
            addVehicle()
            val viewModel = viewModel(saveParkingLocation = ThrowingSaveParkingLocation())

            viewModel.events.test {
                viewModel.saveCurrentLocation()
                assertEquals(ParkingEvent.SaveFailed, awaitItem())
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
    fun `adding a duplicate tells the caller so the dialog can stay open`() =
        runTest {
            addVehicle("A")
            val viewModel = viewModel()
            var added: Boolean? = null

            viewModel.events.test {
                viewModel.addVehicle("B", "AA:BB:CC:DD:EE:01", "Car") { added = it }
                assertEquals(ParkingEvent.DuplicateVehicle, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(false, added)
        }

    @Test
    fun `the background rationale is asked once and then stays silent`() =
        runTest {
            addVehicle()
            backgroundLocation = false
            val viewModel = viewModel(FakeSettingsRepository(backgroundRationaleShown = false))

            viewModel.uiState.test {
                var state = awaitItem()
                while (!state.isReady || state.vehicles.isEmpty()) state = awaitItem()
                assertTrue(state.needsBackgroundLocationRationale)

                viewModel.markBackgroundRationaleShown()
                while (state.needsBackgroundLocationRationale) state = awaitItem()

                assertFalse(state.needsBackgroundLocationRationale)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the background rationale waits until a vehicle exists`() =
        runTest {
            backgroundLocation = false
            val viewModel = viewModel()

            viewModel.uiState.test {
                var state = awaitItem()
                while (!state.isReady) state = awaitItem()
                assertTrue(state.vehicles.isEmpty())
                assertFalse(state.needsBackgroundLocationRationale)
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
                while (state.selectedLocationId != location.id) state = awaitItem()

                viewModel.deleteLocation(location)
                while (state.locations.size != 1 || state.selectedLocationId != null) {
                    state = awaitItem()
                }

                assertNull(state.selectedLocationId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an approximate grant is not treated as a usable location permission`() =
        runTest {
            addVehicle()
            accuracy = LocationAccuracy.COARSE
            val viewModel = viewModel()

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.locationAccuracy != LocationAccuracy.COARSE) state = awaitItem()
                assertFalse(state.hasLocationPermission)
                assertTrue(state.isLocationApproximate)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saving without the location permission asks for it`() =
        runTest {
            addVehicle()
            accuracy = LocationAccuracy.NONE
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.saveCurrentLocation()
                assertEquals(ParkingEvent.PermissionMissing, awaitItem())
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
                while (state.hasMonitoringPermission || state.isLocationEnabled) {
                    state = awaitItem()
                }
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

    @Test
    fun `clearing the history uses the vehicle that was shown`() =
        runTest {
            val first = addVehicle("A")
            val second = addVehicle("B")
            val fakeParking =
                FakeParkingRepository(
                    mutableMapOf(
                        first to listOf(ParkingLocationEntity(1, first, 1.0, 1.0, 1L)),
                        second to listOf(ParkingLocationEntity(2, second, 2.0, 2.0, 2L)),
                    ),
                )
            val viewModel = viewModel(parkingRepository = fakeParking)

            viewModel.clearHistory(first)

            assertNull(fakeParking.latestLocationOnce(first))
            assertNotNull(fakeParking.latestLocationOnce(second))
        }

    @Test
    fun `a failed add reports an operation failure`() =
        runTest {
            val viewModel = viewModel(vehicleRepository = FailingVehicleRepository(vehicles))

            viewModel.events.test {
                viewModel.addVehicle("B", "AA:BB:CC:DD:EE:77", "Car")

                assertEquals(ParkingEvent.OperationFailed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failed delete reports an operation failure`() =
        runTest {
            val id = addVehicle()
            parking.saveLocation(id, 1.0, 1.0)
            val location = parking.latestLocationOnce(id)!!
            val viewModel = viewModel(parkingRepository = FailingParkingRepository(parking))

            viewModel.events.test {
                viewModel.deleteLocation(location)

                assertEquals(ParkingEvent.OperationFailed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
