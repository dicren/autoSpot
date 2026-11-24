package com.dicren.autospot.util

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `without location permissions the accuracy is none`() {
        assertEquals(LocationAccuracy.NONE, Permissions.locationAccuracy(app))
        assertFalse(Permissions.hasLocation(app))
        assertFalse(Permissions.isLocationApproximate(app))
    }

    @Test
    fun `an approximate grant is not enough to save a spot`() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

        assertEquals(LocationAccuracy.COARSE, Permissions.locationAccuracy(app))
        assertTrue(Permissions.isLocationApproximate(app))
        assertFalse(Permissions.hasLocation(app))
        assertFalse(Permissions.canMonitor(app))
    }

    @Test
    fun `precise wins over approximate`() {
        shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        assertEquals(LocationAccuracy.PRECISE, Permissions.locationAccuracy(app))
        assertTrue(Permissions.hasLocation(app))
        assertFalse(Permissions.isLocationApproximate(app))
    }

    @Test
    fun `monitoring also requires background location and bluetooth`() {
        shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        assertFalse(Permissions.canMonitor(app))

        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertTrue(Permissions.canMonitor(app))
    }
}
