package com.dicren.autospot.ui.parking

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dicren.autospot.data.VehicleEntity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ParkingEffectsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class RefusingContext(
        base: Context,
    ) : ContextWrapper(base) {
        override fun startForegroundService(service: Intent): ComponentName? =
            throw IllegalStateException("background start refused")
    }

    @Test
    fun `a refused monitor start asks the screen to explain it`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        var failed = false

        composeRule.setContent {
            ParkingMonitorEffect(
                state =
                    ParkingUiState(
                        vehicles =
                            listOf(
                                VehicleEntity(
                                    id = 1,
                                    name = "Car",
                                    bluetoothAddress = "AA:BB:CC:DD:EE:01",
                                    bluetoothName = "Car",
                                    isActive = true,
                                ),
                            ),
                        hasMonitoringPermission = true,
                        isReady = true,
                    ),
                context = RefusingContext(app),
                onStartFailed = { failed = true },
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { failed }
        assertTrue(failed)
    }
}
