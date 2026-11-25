package com.dicren.autospot.ui.parking

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class MapFocusTest {
    @Test
    fun `a camera looking at the marker has no drift`() {
        val spot = LatLng(42.0, -5.0)

        assertEquals(0.0, markerDriftPx(17f, spot, spot), 0.0)
    }

    @Test
    fun `zoom zero puts the whole world in 256 pixels`() {
        // Half a world away is half of the map: 128 px from the middle, by definition of the
        // zoom levels the Maps SDK uses.
        assertEquals(128.0, markerDriftPx(0f, LatLng(0.0, 180.0), LatLng(0.0, 0.0)), 0.001)
    }

    @Test
    fun `each zoom level doubles the drift on screen`() {
        val target = LatLng(42.01, -5.0)
        val spot = LatLng(42.0, -5.0)

        assertEquals(markerDriftPx(16f, target, spot) * 2, markerDriftPx(17f, target, spot), 0.001)
    }

    @Test
    fun `the recenter button does not appear in the middle of a gesture that started on the spot`() {
        // The user is dragging the map away from a centred spot: nothing to offer yet.
        assertFalse(
            recenterButtonVisibility(
                wasVisible = false,
                isMoving = true,
                driftPx = 400.0,
                deadZonePx = 60f,
            ),
        )
    }

    @Test
    fun `the recenter button does not vanish while the user is still moving`() {
        // Already far away and still dragging: the button must stay under the finger.
        assertTrue(
            recenterButtonVisibility(
                wasVisible = true,
                isMoving = true,
                driftPx = 0.0,
                deadZonePx = 60f,
            ),
        )
    }

    @Test
    fun `once the map settles the distance decides`() {
        assertTrue(
            recenterButtonVisibility(
                wasVisible = false,
                isMoving = false,
                driftPx = 60.5,
                deadZonePx = 60f,
            ),
        )
        assertFalse(
            recenterButtonVisibility(
                wasVisible = true,
                isMoving = false,
                driftPx = 59.5,
                deadZonePx = 60f,
            ),
        )
    }

    @Test
    fun `a degree of latitude moves the marker further than a degree of longitude`() {
        val spot = LatLng(42.0, -5.0)
        val north = markerDriftPx(17f, LatLng(42.01, -5.0), spot)
        val east = markerDriftPx(17f, LatLng(42.0, -4.99), spot)

        assertTrue("North should drift further than east", north > east)
        assertEquals(1 / cos(Math.toRadians(42.0)), north / east, 1e-6)
    }
}
