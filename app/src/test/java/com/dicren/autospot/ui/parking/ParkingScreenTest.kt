package com.dicren.autospot.ui.parking

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dicren.autospot.R
import com.dicren.autospot.data.SettingsRepository
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.domain.ResolveLocationAddressUseCase
import com.dicren.autospot.domain.SaveParkingLocation
import com.dicren.autospot.domain.SaveResult
import com.dicren.autospot.ui.theme.AutoSpotTheme
import com.dicren.autospot.util.FakeLocateSelf
import com.dicren.autospot.util.FakeParkingRepository
import com.dicren.autospot.util.FakeReverseGeocoder
import com.dicren.autospot.util.FakeVehicleRepository
import com.dicren.autospot.util.LocationAccuracy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ParkingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val vehicle =
        VehicleEntity(
            id = 1,
            name = "Car",
            bluetoothAddress = "AA:BB:CC:DD:EE:01",
            bluetoothName = "Car BT",
            isActive = true,
        )

    private class RecordingSettingsRepository : SettingsRepository {
        private val onboarding = MutableStateFlow(true)
        private val rationale = MutableStateFlow(false)
        var rationaleMarked = false
            private set

        override val isOnboardingCompleted: Flow<Boolean> = onboarding
        override val isBackgroundLocationRationaleShown: Flow<Boolean> = rationale

        override suspend fun setOnboardingCompleted() {
            onboarding.value = true
        }

        override suspend fun setBackgroundLocationRationaleShown() {
            rationaleMarked = true
            rationale.value = true
        }
    }

    private class NoSave : SaveParkingLocation {
        override suspend fun save(vehicleId: Long?): SaveResult = SaveResult.NoVehicle
    }

    @Test
    fun `the background rationale is not recorded until the user dismisses it`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rationaleTitle = context.getString(R.string.background_location_title)
        val notNow = context.getString(R.string.not_now)
        val settings = RecordingSettingsRepository()
        val viewModel =
            ParkingViewModel(
                vehicleRepository = FakeVehicleRepository(listOf(vehicle)),
                parkingRepository = FakeParkingRepository(),
                settingsRepository = settings,
                saveParkingLocation = NoSave(),
                resolveLocationAddress =
                    ResolveLocationAddressUseCase(
                        parkingRepository = FakeParkingRepository(),
                        reverseGeocoder = FakeReverseGeocoder(),
                    ),
                locateSelf = FakeLocateSelf(),
                hasMonitoringPermission = { true },
                locationAccuracy = { LocationAccuracy.PRECISE },
                hasBackgroundLocationPermission = { false },
                isLocationEnabled = { true },
            )

        composeRule.setContent {
            AutoSpotTheme {
                ParkingScreen(viewModel = viewModel)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText(rationaleTitle)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertFalse(settings.rationaleMarked)

        composeRule.onNodeWithText(notNow).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { settings.rationaleMarked }
        assertTrue(settings.rationaleMarked)
    }
}
