package com.dicren.autospot

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.util.FakeParkingRepository
import com.dicren.autospot.util.FakeVehicleRepository
import com.dicren.autospot.util.TestAppContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Regression guard for the headless navigation entry point.
 *
 * `NavigateActivity` reads the location from Room, so it cannot finish before `onResume()`
 * completes. An invisible theme (`Theme.NoDisplay`) makes Android throw
 * "did not call finish() prior to onResume() completing"; the activity must use a translucent
 * theme instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigateActivityTest {
    private val vehicle =
        VehicleEntity(
            id = 7,
            name = "Car",
            bluetoothAddress = "AA:BB:CC:DD:EE:01",
            bluetoothName = "Car BT",
            isActive = true,
        )

    private lateinit var app: AutoSpotApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        app.containerOverride = null
    }

    private fun launch(
        withLocation: Boolean,
        vehicleId: Long? = null,
    ): NavigateActivity {
        val history =
            if (withLocation) {
                mutableMapOf(
                    vehicle.id to
                        listOf(
                            ParkingLocationEntity(
                                id = 1,
                                vehicleId = vehicle.id,
                                latitude = 42.0,
                                longitude = -5.0,
                                timestamp = 1_700_000_000_000L,
                            ),
                        ),
                )
            } else {
                mutableMapOf()
            }
        app.containerOverride =
            TestAppContainer(
                vehicleRepository = FakeVehicleRepository(listOf(vehicle)),
                parkingRepository = FakeParkingRepository(history),
            )

        val intent = Intent(app, NavigateActivity::class.java)
        if (vehicleId != null) intent.putExtra(NavigateActivity.EXTRA_VEHICLE_ID, vehicleId)

        val controller = Robolectric.buildActivity(NavigateActivity::class.java, intent).setup()
        shadowOf(Looper.getMainLooper()).idle()
        return controller.get()
    }

    @Test
    fun `opens maps for the requested vehicle and finishes`() {
        val activity = launch(withLocation = true, vehicleId = vehicle.id)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals("google.navigation:q=42.0,-5.0&mode=w", started.data.toString())
        assertEquals("com.google.android.apps.maps", started.`package`)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `falls back to the active vehicle`() {
        val activity = launch(withLocation = true)

        assertEquals(
            "google.navigation:q=42.0,-5.0&mode=w",
            shadowOf(activity).nextStartedActivity.data.toString(),
        )
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `without a saved location it explains and finishes`() {
        val activity = launch(withLocation = false)

        assertEquals("No location saved", ShadowToast.getTextOfLatestToast())
        assertTrue(activity.isFinishing)
    }
}
