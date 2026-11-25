package com.dicren.autospot.ui.parking

import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.util.LocationAccuracy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingUiStateTest {
    private val vehicle =
        VehicleEntity(
            id = 1,
            name = "Car",
            bluetoothAddress = "AA:BB:CC:DD:EE:01",
            bluetoothName = "Car",
            isActive = true,
        )

    private fun state(
        isReady: Boolean = true,
        accuracy: LocationAccuracy = LocationAccuracy.PRECISE,
        backgroundGranted: Boolean = false,
        vehicles: List<VehicleEntity> = listOf(vehicle),
        rationaleShown: Boolean = false,
    ) = ParkingUiState(
        vehicles = vehicles,
        isReady = isReady,
        locationAccuracy = accuracy,
        hasBackgroundLocationPermission = backgroundGranted,
        backgroundLocationRationaleShown = rationaleShown,
    )

    @Test
    fun `asks for background location once precise location is granted`() {
        assertTrue(state().needsBackgroundLocationRationale)
        assertFalse(state(rationaleShown = true).needsBackgroundLocationRationale)
    }

    @Test
    fun `does not ask before the first real emission`() {
        assertFalse(state(isReady = false).needsBackgroundLocationRationale)
    }

    @Test
    fun `does not ask while location is missing or approximate`() {
        assertFalse(state(accuracy = LocationAccuracy.NONE).needsBackgroundLocationRationale)
        assertFalse(state(accuracy = LocationAccuracy.COARSE).needsBackgroundLocationRationale)
    }

    @Test
    fun `does not ask when background location is already granted`() {
        assertFalse(state(backgroundGranted = true).needsBackgroundLocationRationale)
    }

    @Test
    fun `does not ask before a vehicle is configured`() {
        assertFalse(state(vehicles = emptyList()).needsBackgroundLocationRationale)
    }

    @Test
    fun `approximate location is reported as such`() {
        assertTrue(state(accuracy = LocationAccuracy.COARSE).isLocationApproximate)
        assertFalse(state(accuracy = LocationAccuracy.COARSE).hasLocationPermission)
        assertFalse(state(accuracy = LocationAccuracy.NONE).isLocationApproximate)
        assertFalse(state(accuracy = LocationAccuracy.PRECISE).isLocationApproximate)
    }

    /** Newest first, like the repository returns them. */
    private fun history(vararg ids: Long) =
        ids.map { id ->
            ParkingLocationEntity(
                id = id,
                vehicleId = vehicle.id,
                latitude = 42.0,
                longitude = -5.0,
                timestamp = id,
            )
        }

    @Test
    fun `a swipe steps through the newest-first history and stops at both ends`() {
        val locations = history(3, 2, 1)

        val newest = ParkingUiState(locations = locations)
        assertEquals(0, newest.displayIndex)
        assertTrue(newest.canStepOlder)
        assertFalse(newest.canStepNewer)
        assertEquals(1, newest.stepIndex(older = true))
        assertNull(newest.stepIndex(older = false))

        val middle = newest.copy(selectedLocationId = 2)
        assertEquals(1, middle.displayIndex)
        assertTrue(middle.canStepOlder)
        assertTrue(middle.canStepNewer)
        assertEquals(2, middle.stepIndex(older = true))
        assertEquals(0, middle.stepIndex(older = false))

        val oldest = newest.copy(selectedLocationId = 1)
        assertEquals(2, oldest.displayIndex)
        assertFalse(oldest.canStepOlder)
        assertTrue(oldest.canStepNewer)
        assertNull(oldest.stepIndex(older = true))
        assertEquals(1, oldest.stepIndex(older = false))
    }

    @Test
    fun `a single spot has nowhere to swipe to`() {
        val single = ParkingUiState(locations = history(5))

        assertFalse(single.canStepOlder)
        assertFalse(single.canStepNewer)
        assertNull(single.stepIndex(older = true))
        assertNull(single.stepIndex(older = false))
    }

    @Test
    fun `an empty history has nowhere to swipe to`() {
        val empty = ParkingUiState()

        assertEquals(0, empty.displayIndex)
        assertFalse(empty.canStepOlder)
        assertFalse(empty.canStepNewer)
        assertNull(empty.stepIndex(older = true))
        assertNull(empty.stepIndex(older = false))
    }

    private fun spot(
        id: Long,
        vehicleId: Long = vehicle.id,
        timestamp: Long = id,
    ) = ParkingLocationEntity(
        id = id,
        vehicleId = vehicleId,
        latitude = 42.0,
        longitude = -5.0,
        timestamp = timestamp,
    )

    @Test
    fun `each scope of the overview draws the spots it is about`() {
        val mine = history(3, 2, 1)
        val otherVehicle = spot(id = 9, vehicleId = 2, timestamp = 9)
        val state = ParkingUiState(locations = mine, allLocations = listOf(otherVehicle) + mine)

        assertFalse("Nothing is an overview until it is opened", state.isOverviewing)
        assertEquals(mine, state.overviewLocations)

        val oneVehicle = state.copy(overview = MapOverview(MapScope.ACTIVE_VEHICLE))
        assertTrue(oneVehicle.isOverviewing)
        assertFalse(oneVehicle.isShowingAllVehicles)
        assertEquals(mine, oneVehicle.overviewLocations)

        val everyVehicle = state.copy(overview = MapOverview(MapScope.ALL_VEHICLES))
        assertTrue(everyVehicle.isShowingAllVehicles)
        assertEquals(4, everyVehicle.overviewLocations.size)
    }

    /** The mode exists to see every spot, so no card is shown until a group is picked. */
    @Test
    fun `the overview shows no card until a group is picked`() {
        val state =
            ParkingUiState(
                locations = history(3, 2, 1),
                overview = MapOverview(MapScope.ACTIVE_VEHICLE),
            )

        assertFalse(state.isClusterFocused)
        assertNull(state.displayLocation)
        assertEquals(3, state.historyLocations.size)
    }

    @Test
    fun `a picked group pages only through its own spots`() {
        val state =
            ParkingUiState(
                locations = history(5, 4, 3, 2, 1),
                overview = MapOverview(MapScope.ACTIVE_VEHICLE, setOf(4L, 2L)),
            )

        assertTrue(state.isClusterFocused)
        assertEquals(listOf(4L, 2L), state.historyLocations.map { it.id })
        assertEquals(4L, state.displayLocation?.id)
        assertEquals(0, state.displayIndex)
        assertTrue(state.canStepOlder)
        assertFalse(state.canStepNewer)
        assertEquals(1, state.stepIndex(older = true))
        assertNull(state.stepIndex(older = false))

        val oldest = state.copy(selectedLocationId = 2)
        assertEquals(1, oldest.displayIndex)
        assertNull(oldest.stepIndex(older = true))
        assertTrue(oldest.canStepNewer)
    }

    @Test
    fun `a group of a single spot behaves like a one-entry history`() {
        val state =
            ParkingUiState(
                locations = history(3, 2, 1),
                overview = MapOverview(MapScope.ACTIVE_VEHICLE, setOf(2L)),
            )

        assertEquals(listOf(2L), state.historyLocations.map { it.id })
        assertEquals(2L, state.displayLocation?.id)
        assertFalse(state.canStepOlder)
        assertFalse(state.canStepNewer)
        assertNull(state.stepIndex(older = true))
    }

    @Test
    fun `the newest spot of each vehicle is the one drawn as a car`() {
        val older = spot(id = 1, timestamp = 1)
        val newestMine = spot(id = 2, timestamp = 2)
        val newestOther = spot(id = 7, vehicleId = 2, timestamp = 7)
        val olderOther = spot(id = 6, vehicleId = 2, timestamp = 6)

        val state =
            ParkingUiState(
                allLocations = listOf(newestMine, newestOther, olderOther, older),
                overview = MapOverview(MapScope.ALL_VEHICLES),
            )

        assertEquals(setOf(newestMine.id, newestOther.id), state.latestSpotIds)
    }
}
