package com.dicren.autospot.ui.parking

import com.dicren.autospot.util.LocationAccuracy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingUiStateTest {
    private fun state(
        isReady: Boolean = true,
        accuracy: LocationAccuracy = LocationAccuracy.PRECISE,
        backgroundGranted: Boolean = false,
    ) = ParkingUiState(
        isReady = isReady,
        locationAccuracy = accuracy,
        hasBackgroundLocationPermission = backgroundGranted,
    )

    @Test
    fun `asks for background location once precise location is granted`() {
        assertTrue(state().needsBackgroundLocationRationale(alreadyAsked = false))
        assertFalse(state().needsBackgroundLocationRationale(alreadyAsked = true))
    }

    @Test
    fun `does not ask before the first real emission`() {
        assertFalse(state(isReady = false).needsBackgroundLocationRationale(alreadyAsked = false))
    }

    @Test
    fun `does not ask while location is missing or approximate`() {
        assertFalse(
            state(accuracy = LocationAccuracy.NONE).needsBackgroundLocationRationale(alreadyAsked = false),
        )
        assertFalse(
            state(accuracy = LocationAccuracy.COARSE).needsBackgroundLocationRationale(alreadyAsked = false),
        )
    }

    @Test
    fun `does not ask when background location is already granted`() {
        assertFalse(
            state(backgroundGranted = true).needsBackgroundLocationRationale(alreadyAsked = false),
        )
    }

    @Test
    fun `approximate location is reported as such`() {
        assertTrue(state(accuracy = LocationAccuracy.COARSE).isLocationApproximate)
        assertFalse(state(accuracy = LocationAccuracy.COARSE).hasLocationPermission)
        assertFalse(state(accuracy = LocationAccuracy.NONE).isLocationApproximate)
        assertFalse(state(accuracy = LocationAccuracy.PRECISE).isLocationApproximate)
    }
}
