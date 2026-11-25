package com.dicren.autospot.ui.parking.components

import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.ui.theme.AutoSpotTheme
import com.dicren.autospot.util.BluetoothDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ParkingComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val formatter =
        ParkingTimeFormatter(
            ApplicationProvider.getApplicationContext<android.content.Context>().resources,
        )

    private val vehicle =
        VehicleEntity(
            id = 1,
            name = "Mi coche",
            bluetoothAddress = "AA:BB:CC:DD:EE:01",
            bluetoothName = "R-LINK",
            isActive = true,
        )

    private val location =
        ParkingLocationEntity(
            id = 10,
            vehicleId = 1,
            latitude = 42.0,
            longitude = -5.0,
            timestamp = System.currentTimeMillis() - 5 * 60_000L,
        )

    /** A clock five minutes after the saved spot, so the rendered text is deterministic. */
    private val nowMillis = location.timestamp + 5 * 60_000L

    @Test
    fun `the empty state invites the user to save a location`() {
        var clicked = false
        composeRule.setContent {
            AutoSpotTheme {
                NoLocationCard(isSaving = false, onSaveLocationClick = { clicked = true })
            }
        }

        composeRule.onNodeWithText("No saved location").assertIsDisplayed()
        composeRule.onNodeWithText("Save current location").assertIsEnabled().performClick()

        assertTrue(clicked)
    }

    @Test
    fun `saving disables the button and shows progress`() {
        composeRule.setContent {
            AutoSpotTheme {
                NoLocationCard(isSaving = true, onSaveLocationClick = {})
            }
        }

        composeRule.onNodeWithText("Save current location").assertIsNotEnabled()
    }

    @Test
    fun `the vehicle card shows the active vehicle and opens settings`() {
        var opened = false
        composeRule.setContent {
            AutoSpotTheme {
                VehicleCard(vehicle = vehicle, onSettingsClick = { opened = true })
            }
        }

        composeRule.onNodeWithText("Mi coche").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Vehicles").performClick()

        assertTrue(opened)
    }

    @Test
    fun `the vehicle card explains when nothing is configured`() {
        composeRule.setContent {
            AutoSpotTheme { VehicleCard(vehicle = null, onSettingsClick = {}) }
        }

        composeRule.onNodeWithText("No vehicle configured").assertIsDisplayed()
    }

    @Test
    fun `the location card shows the time and hides history for a single entry`() {
        composeRule.setContent {
            AutoSpotTheme {
                LocationCard(
                    location = location,
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    historyCount = 1,
                    isFromHistory = false,
                    onShareClick = {},
                    onHistoryClick = {},
                    onBackToLatestClick = {},
                    onCenterMapClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Parked").assertIsDisplayed()
        composeRule.onNodeWithText("5 minutes ago").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("History").assertDoesNotExist()
    }

    @Test
    fun `a history selection can go back to the latest location`() {
        var backClicked = false
        composeRule.setContent {
            AutoSpotTheme {
                LocationCard(
                    location = location,
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    historyCount = 3,
                    isFromHistory = true,
                    onShareClick = {},
                    onHistoryClick = {},
                    onBackToLatestClick = { backClicked = true },
                    onCenterMapClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("History").assertIsDisplayed()
        composeRule.onNodeWithText("Back to latest").performClick()

        assertTrue(backClicked)
    }

    /**
     * The floating cards use a translucent surface, which `contentColorFor` cannot map back to a
     * scheme color. Without an explicit content color the icons fell back to black and vanished
     * over the dark theme, which is where the whole action row of the location card went.
     */
    @Test
    fun `the floating cards keep a readable content color in the dark theme`() {
        var expectedOnSurface: Color? = null
        var cardContentColor: Color? = null

        composeRule.setContent {
            AutoSpotTheme(darkTheme = true, dynamicColor = false) {
                expectedOnSurface = MaterialTheme.colorScheme.onSurface
                Card(colors = parkingCardColors()) {
                    val contentColor = LocalContentColor.current
                    SideEffect { cardContentColor = contentColor }
                }
            }
        }
        composeRule.waitForIdle()

        assertNotEquals(Color.Unspecified, cardContentColor)
        assertEquals(expectedOnSurface, cardContentColor)
    }

    @Test
    fun `the permission banner explains what is missing`() {
        var granted = false
        composeRule.setContent {
            AutoSpotTheme { PermissionBanner(onGrantClick = { granted = true }) }
        }

        composeRule.onNodeWithText("Permissions required").assertIsDisplayed()
        composeRule.onNodeWithText("Grant permissions").performClick()

        assertTrue(granted)
    }

    @Test
    fun `the permission banner names approximate location as the cause`() {
        composeRule.setContent {
            AutoSpotTheme {
                PermissionBanner(onGrantClick = {}, isApproximateOnly = true)
            }
        }

        composeRule.onNodeWithText("Precise location required").assertIsDisplayed()
    }

    @Test
    fun `the history sheet lists entries and offers to clear them`() {
        var cleared = false
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations = listOf(location),
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = { cleared = true },
                )
            }
        }

        composeRule.onNodeWithText("History").assertIsDisplayed()
        composeRule.onNodeWithText("5 minutes ago").assertIsDisplayed()
        composeRule.onNodeWithText("Clear all").performClick()

        assertTrue(cleared)
    }

    @Test
    fun `the history sheet explains when it is empty`() {
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations = emptyList(),
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = {},
                )
            }
        }

        composeRule.onNodeWithText("No parking history").assertIsDisplayed()
        composeRule.onNodeWithText("Clear all").assertDoesNotExist()
    }

    @Test
    fun `the edit dialog cannot save without a Bluetooth device`() {
        composeRule.setContent {
            AutoSpotTheme {
                VehicleEditDialog(
                    vehicle = null,
                    canReadBluetooth = false,
                    devices = emptyList(),
                    onDismiss = {},
                    onSave = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule
            .onNodeWithText(
                "Allow Bluetooth access to list your paired devices.",
            ).assertIsDisplayed()
    }

    @Test
    fun `the delete confirmation is only shown when asked for`() {
        var deleted: VehicleEntity? = null
        composeRule.setContent {
            AutoSpotTheme {
                VehiclesSheet(
                    vehicles = listOf(vehicle),
                    activeVehicle = vehicle,
                    canReadBluetooth = true,
                    devices = emptyList(),
                    onVehicleClick = {},
                    onAddVehicle = { _, _, _, _ -> },
                    onEditVehicle = { _, _ -> },
                    onDeleteVehicle = { deleted = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete vehicle").assertIsDisplayed()
        composeRule.onNodeWithText("Mi coche will be deleted", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(vehicle.id, deleted?.id)
    }

    @Test
    fun `a failed edit keeps the dialog open instead of discarding the changes`() {
        val device = BluetoothDeviceInfo(name = "R-LINK", address = "AA:BB:CC:DD:EE:01")
        var editResult: ((Boolean) -> Unit)? = null

        composeRule.setContent {
            AutoSpotTheme {
                VehiclesSheet(
                    vehicles = listOf(vehicle),
                    activeVehicle = vehicle,
                    canReadBluetooth = true,
                    devices = listOf(device),
                    onVehicleClick = {},
                    onAddVehicle = { _, _, _, _ -> },
                    onEditVehicle = { _, onResult -> editResult = onResult },
                    onDeleteVehicle = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Edit vehicle").performClick()
        // The edited vehicle already has a Bluetooth address, so Save is enabled right away.
        composeRule.onNodeWithText("Save").performClick()

        // The repository has not answered yet; nothing may close the dialog.
        composeRule.onNodeWithText("Edit vehicle").assertIsDisplayed()

        editResult?.invoke(false)
        composeRule.onNodeWithText("Edit vehicle").assertIsDisplayed()

        editResult?.invoke(true)
        composeRule.onNodeWithText("Edit vehicle").assertDoesNotExist()
    }

    @Test
    fun `a duplicate add keeps the dialog open instead of discarding the changes`() {
        val device = BluetoothDeviceInfo(name = "R-LINK", address = "AA:BB:CC:DD:EE:01")
        var addResult: ((Boolean) -> Unit)? = null

        composeRule.setContent {
            AutoSpotTheme {
                VehiclesSheet(
                    vehicles = emptyList(),
                    activeVehicle = null,
                    canReadBluetooth = true,
                    devices = listOf(device),
                    onVehicleClick = {},
                    onAddVehicle = { _, _, _, onResult -> addResult = onResult },
                    onEditVehicle = { _, _ -> },
                    onDeleteVehicle = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add vehicle").performClick()
        composeRule.onNodeWithText("Select Bluetooth device").performClick()
        composeRule.onNodeWithText("R-LINK").performClick()
        composeRule.onNodeWithText("Save").performClick()

        // The repository has not answered yet; nothing may close the dialog.
        composeRule.onNodeWithText("Add vehicle").assertIsDisplayed()

        addResult?.invoke(false)
        composeRule.onNodeWithText("Add vehicle").assertIsDisplayed()

        addResult?.invoke(true)
        composeRule.onNodeWithText("Add vehicle").assertDoesNotExist()
    }
}
