package com.dicren.autospot.ui.parking

import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.util.LocationAccuracy
import org.junit.Assert.assertFalse
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
}
