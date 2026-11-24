package com.dicren.autospot.receiver

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.service.BluetoothMonitorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootReceiverTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    private val vehicle =
        VehicleEntity(
            id = 1,
            name = "Car",
            bluetoothAddress = "AA:BB:CC:DD:EE:01",
            bluetoothName = "Car multimedia",
            isActive = true,
        )

    @Test
    fun `starts the monitor when there are vehicles and permissions`() {
        val started = startMonitorIfNeeded(application, listOf(vehicle), canMonitor = true)

        assertTrue(started)
        assertEquals(
            BluetoothMonitorService::class.java.name,
            shadowOf(application).nextStartedService?.component?.className,
        )
    }

    @Test
    fun `does not start without vehicles`() {
        val started = startMonitorIfNeeded(application, emptyList(), canMonitor = true)

        assertFalse(started)
        assertNull(shadowOf(application).nextStartedService)
    }

    @Test
    fun `does not start without the monitoring permissions`() {
        val started = startMonitorIfNeeded(application, listOf(vehicle), canMonitor = false)

        assertFalse(started)
        assertNull(shadowOf(application).nextStartedService)
    }
}
