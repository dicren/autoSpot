package com.dicren.autospot.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.dicren.autospot.AutoSpotApp
import com.dicren.autospot.R
import com.dicren.autospot.core.Clock
import com.dicren.autospot.data.AddVehicleResult
import com.dicren.autospot.util.FakeVehicleRepository
import com.dicren.autospot.util.TestAppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BluetoothMonitorServiceTest {
    private class FakeClock(
        var now: Long = NOW,
    ) : Clock {
        override fun nowMillis(): Long = now
    }

    private val app: AutoSpotApp = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val vehicles = FakeVehicleRepository()

    @Before
    fun setUp() {
        app.containerOverride =
            TestAppContainer(
                clock = clock,
                vehicleRepository = vehicles,
            )
        shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    @After
    fun tearDown() {
        app.containerOverride = null
    }

    private fun addVehicle(): Long =
        runBlocking {
            (vehicles.addVehicle("Car", ADDRESS, "Car") as AddVehicleResult.Added).id
        }

    private fun startMonitor(): ServiceController<BluetoothMonitorService> =
        Robolectric
            .buildService(BluetoothMonitorService::class.java)
            .create()
            .startCommand(0, 1)

    @Suppress("DEPRECATION")
    private fun sendDisconnect(address: String = ADDRESS) {
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
        val intent =
            Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED).apply {
                putExtra(BluetoothDevice.EXTRA_DEVICE, device)
            }
        app.sendBroadcast(intent)
        // Broadcast delivery and the receiver callback run on the main looper.
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitStartedService(): Intent? =
        runBlocking {
            withTimeoutOrNull(3_000) {
                var started: Intent? = null
                while (started == null) {
                    shadowOf(Looper.getMainLooper()).idle()
                    started = shadowOf(app).peekNextStartedService()
                    if (started == null) delay(10)
                }
                started
            }
        }

    @Test
    fun `a configured vehicle disconnection starts the save service`() {
        val vehicleId = addVehicle()
        val controller = startMonitor()

        sendDisconnect()

        val started = awaitStartedService()
        assertNotNull(started)
        assertEquals(LocationSaveService::class.java.name, started?.component?.className)
        assertEquals(vehicleId, started?.getLongExtra(LocationSaveService.EXTRA_VEHICLE_ID, -1L))

        controller.destroy()
    }

    @Test
    fun `an unknown device does not start a save`() {
        addVehicle()
        val controller = startMonitor()

        sendDisconnect("AA:BB:CC:DD:EE:99")
        runBlocking { delay(300) }

        assertNull(shadowOf(app).peekNextStartedService())
        controller.destroy()
    }

    @Test
    fun `a burst of disconnections only starts one save`() {
        addVehicle()
        val controller = startMonitor()

        sendDisconnect()
        assertNotNull(awaitStartedService())
        shadowOf(app).clearStartedServices()

        sendDisconnect()
        runBlocking { delay(300) }

        assertNull(shadowOf(app).peekNextStartedService())
        controller.destroy()
    }

    @Test
    fun `the monitor stops itself without the monitoring permissions`() {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val controller = Robolectric.buildService(BluetoothMonitorService::class.java).create()

        val result = controller.get().onStartCommand(Intent(), 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun `losing permissions while running stops the monitor and saves nothing`() {
        addVehicle()
        val controller = startMonitor()
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        sendDisconnect()
        runBlocking { delay(300) }

        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertNull(shadowOf(app).peekNextStartedService())
        controller.destroy()
    }

    @Test
    fun `a refused foreground start shuts the service down`() {
        val controller = Robolectric.buildService(BluetoothMonitorService::class.java).create()
        shadowOf(controller.get()).setThrowInStartForeground(SecurityException("denied"))

        val result = controller.get().onStartCommand(Intent(), 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)

        val manager = app.getSystemService(NotificationManager::class.java)
        val posted = shadowOf(manager).allNotifications
        assertTrue(
            posted.any {
                it.extras.getString(Notification.EXTRA_TITLE) ==
                    app.getString(R.string.monitor_failed_title)
            },
        )

        controller.destroy()
    }

    private companion object {
        const val ADDRESS = "AA:BB:CC:DD:EE:01"
        const val NOW = 1_700_000_000_000L
    }
}
