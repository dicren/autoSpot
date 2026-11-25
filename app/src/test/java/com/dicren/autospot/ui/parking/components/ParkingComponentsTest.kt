package com.dicren.autospot.ui.parking.components

import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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

    private val addressedLocation =
        location.copy(
            street = "Calle Mayor 12",
            locality = "Getafe",
            addressResolved = true,
        )

    /** A spot saved at noon on [day], so the calendar always groups it under that day. */
    private fun spotOn(
        day: LocalDate,
        id: Long,
        street: String? = null,
    ): ParkingLocationEntity =
        ParkingLocationEntity(
            id = id,
            vehicleId = vehicle.id,
            latitude = 42.0,
            longitude = -5.0,
            timestamp =
                day
                    .atTime(12, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli(),
            street = street,
            addressResolved = street != null,
        )

    private fun dayOf(day: LocalDate): String = "$CALENDAR_DAY_TAG_PREFIX${day.toEpochDay()}"

    private fun markOf(day: LocalDate): String = "$CALENDAR_MARK_TAG_PREFIX${day.toEpochDay()}"

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
                    canStepOlder = false,
                    canStepNewer = false,
                    onShareClick = {},
                    onOpenHistory = {},
                    onBackToLatestClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Parked").assertIsDisplayed()
        composeRule.onNodeWithText("5 minutes ago").assertIsDisplayed()
        // Nothing to page through and nothing to open, so the grabber stays out of the way.
        composeRule.onNodeWithTag(HISTORY_HANDLE_TAG).assertDoesNotExist()
        // Recentring lives on the map now: the card keeps its width for the address.
        composeRule.onNodeWithContentDescription("Center map").assertDoesNotExist()
    }

    @Test
    fun `the location card shows the street and the town instead of the generic title`() {
        composeRule.setContent {
            AutoSpotTheme {
                LocationCard(
                    location = addressedLocation,
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    historyCount = 1,
                    isFromHistory = false,
                    canStepOlder = false,
                    canStepNewer = false,
                    onShareClick = {},
                    onOpenHistory = {},
                    onBackToLatestClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Calle Mayor 12, Getafe").assertIsDisplayed()
        composeRule.onNodeWithText("5 minutes ago").assertIsDisplayed()
        composeRule.onNodeWithText("Parked").assertDoesNotExist()
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
                    canStepOlder = true,
                    canStepNewer = true,
                    onShareClick = {},
                    onOpenHistory = {},
                    onBackToLatestClick = { backClicked = true },
                )
            }
        }

        composeRule.onNodeWithTag(HISTORY_HANDLE_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to latest").performClick()

        assertTrue(backClicked)
    }

    /**
     * Every icon-only button explains itself on a long press. On the card it matters most: a
     * crosshair, a clock arrow and a share glyph sit together and none of them is self-evident.
     */
    @Test
    fun `the location card buttons explain themselves on long press`() {
        composeRule.setContent {
            AutoSpotTheme {
                LocationCard(
                    location = location,
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    historyCount = 3,
                    isFromHistory = false,
                    canStepOlder = true,
                    canStepNewer = false,
                    onShareClick = {},
                    onOpenHistory = {},
                    onBackToLatestClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Share location").performTouchInput { longClick() }

        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithText("Share location").fetchSemanticsNodes().isNotEmpty()
        }
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
        composeRule.onNodeWithContentDescription("Clear all").performClick()

        assertTrue(cleared)
    }

    @Test
    fun `the history sheet shows the address where the coordinates used to be`() {
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations = listOf(addressedLocation),
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = {},
                )
            }
        }

        composeRule.onNodeWithText("Calle Mayor 12, Getafe").assertIsDisplayed()
        composeRule.onNodeWithText("42.000000, -5.000000").assertDoesNotExist()
    }

    @Test
    fun `a spot with no address still shows its coordinates in the history`() {
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations = listOf(location),
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = {},
                )
            }
        }

        composeRule.onNodeWithText("42.000000, -5.000000").assertIsDisplayed()
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

    /**
     * The button sits in the header, and it is the only way into the map overview, so it has to
     * stay reachable without reading a tooltip.
     */
    @Test
    fun `the history sheet offers its spots on the map`() {
        var shown = 0
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations = listOf(location),
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = {},
                    onShowOnMap = { shown++ },
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("View this vehicle on the map")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, shown)
    }

    @Test
    fun `an empty history has nothing to show on the map`() {
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations = emptyList(),
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = {},
                    onShowOnMap = {},
                    onShowAllVehiclesOnMap = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("View this vehicle on the map").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("View all vehicles on the map").assertDoesNotExist()
    }

    /**
     * With a single vehicle there is nothing extra to show, so the second button is not offered:
     * two buttons that draw the same spots would only ask the user to choose at random.
     */
    @Test
    fun `showing every vehicle needs more than one vehicle`() {
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations = listOf(location),
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = {},
                    onShowOnMap = {},
                    onShowAllVehiclesOnMap = null,
                )
            }
        }

        composeRule.onNodeWithContentDescription("View all vehicles on the map").assertDoesNotExist()
    }

    @Test
    fun `the overview history says which vehicle each spot belongs to`() {
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations = listOf(location),
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = {},
                    vehicleNames = mapOf(vehicle.id to vehicle.name),
                    showVehicleNames = true,
                )
            }
        }

        composeRule
            .onNodeWithText("Mi coche · 42.000000, -5.000000")
            .assertIsDisplayed()
    }

    /** "Clear all" would promise the vehicle's whole history; a group is only part of it. */
    @Test
    fun `a group is cleared with its own wording`() {
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations = listOf(location),
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = {},
                    isClusterScoped = true,
                )
            }
        }

        composeRule.onNodeWithText("Delete group").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Delete group").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Clear all").assertDoesNotExist()
    }

    @Test
    fun `the history switches between the list and the calendar`() {
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations = listOf(location),
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = {},
                )
            }
        }

        composeRule.onNodeWithTag(HISTORY_CALENDAR_TAG).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Calendar view").performClick()
        composeRule.onNodeWithTag(HISTORY_CALENDAR_TAG).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("List view").performClick()
        composeRule.onNodeWithTag(HISTORY_CALENDAR_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Calendar view").assertIsDisplayed()
    }

    @Test
    fun `the calendar marks the days that hold a spot`() {
        val firstDay = YearMonth.now().atDay(1)
        val secondDay = firstDay.plusDays(1)
        val emptyDay = firstDay.plusDays(2)
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations = listOf(spotOn(firstDay, 1), spotOn(secondDay, 2)),
                    timeFormatter = formatter,
                    nowMillis = System.currentTimeMillis(),
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Calendar view").performClick()

        composeRule.onNodeWithTag(markOf(firstDay), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(markOf(secondDay), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(markOf(emptyDay), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `tapping a marked day lists only that day's spots`() {
        val month = YearMonth.now()
        val olderDay = month.atDay(1)
        val newerDay = month.atDay(2)
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations =
                        listOf(
                            spotOn(olderDay, 1, "Calle Uno"),
                            spotOn(newerDay, 2, "Calle Dos"),
                        ),
                    timeFormatter = formatter,
                    nowMillis = System.currentTimeMillis(),
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Calendar view").performClick()

        // The calendar opens on the newest spot's day.
        composeRule.onNodeWithTag(HISTORY_CALENDAR_TAG).performScrollToNode(hasText("Calle Dos"))
        composeRule.onNodeWithText("Calle Dos").assertIsDisplayed()

        composeRule
            .onNodeWithTag(HISTORY_CALENDAR_TAG)
            .performScrollToNode(hasTestTag(dayOf(olderDay)))
        composeRule.onNodeWithTag(dayOf(olderDay)).performClick()

        composeRule.onNodeWithTag(HISTORY_CALENDAR_TAG).performScrollToNode(hasText("Calle Uno"))
        composeRule.onNodeWithText("Calle Uno").assertIsDisplayed()
        composeRule.onNodeWithText("Calle Dos").assertDoesNotExist()
    }

    /** The day's spots sit under the grid: without growing the sheet the tap looks inert. */
    @Test
    fun `picking a day asks the sheet to show its entries`() {
        var revealed = 0
        val day = YearMonth.now().atDay(1)
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations = listOf(spotOn(day, 1, "Calle Uno")),
                    timeFormatter = formatter,
                    nowMillis = System.currentTimeMillis(),
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = {},
                    onRevealEntries = { revealed++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Calendar view").performClick()
        composeRule.onNodeWithTag(dayOf(day)).performClick()

        assertEquals(1, revealed)
    }

    @Test
    fun `swiping the calendar pages through the months`() {
        val month = YearMonth.now()
        val olderDay = month.minusMonths(1).atDay(5)
        val newerDay = month.atDay(1)
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations = listOf(spotOn(olderDay, 1), spotOn(newerDay, 2)),
                    timeFormatter = formatter,
                    nowMillis = System.currentTimeMillis(),
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Calendar view").performClick()
        composeRule.onNodeWithTag(markOf(olderDay), useUnmergedTree = true).assertDoesNotExist()

        // Right is back in time: the calendar opens on the newest month.
        composeRule.onNodeWithTag(CALENDAR_GRID_TAG).performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(markOf(olderDay), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("Previous month").assertIsNotEnabled()
    }

    /** Paging through months that can never list anything would only make the user guess. */
    @Test
    fun `the calendar stops at the months that hold spots`() {
        composeRule.setContent {
            AutoSpotTheme {
                HistorySheet(
                    locations = listOf(location),
                    timeFormatter = formatter,
                    nowMillis = nowMillis,
                    onLocationSelected = {},
                    onDeleteLocation = {},
                    onRequestClearAll = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Calendar view").performClick()

        composeRule.onNodeWithContentDescription("Previous month").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Next month").assertIsNotEnabled()
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
