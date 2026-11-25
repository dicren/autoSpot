package com.dicren.autospot.ui.parking

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.ui.theme.AutoSpotTheme
import com.dicren.autospot.util.LocationAccuracy
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ParkingTopOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

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

    private val carSpot = LatLng(location.latitude, location.longitude)

    /** Far enough away that no dead zone can make it look centred. */
    private val myPosition = LatLng(42.05, -5.05)

    private fun setContent(
        accuracy: LocationAccuracy,
        onMyLocation: () -> Unit = {},
        hasLocation: Boolean = true,
        myLocation: LatLng? = null,
        cameraTarget: LatLng = carSpot,
    ): CameraPositionState {
        lateinit var cameraPositionState: CameraPositionState
        composeRule.setContent {
            AutoSpotTheme {
                cameraPositionState =
                    rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(cameraTarget, 17f)
                    }
                ParkingTopOverlay(
                    state =
                        ParkingUiState(
                            vehicles = listOf(vehicle),
                            activeVehicle = vehicle,
                            locations = if (hasLocation) listOf(location) else emptyList(),
                            hasMonitoringPermission = true,
                            locationAccuracy = accuracy,
                            isReady = true,
                        ),
                    showBackgroundRationale = false,
                    cameraPositionState = cameraPositionState,
                    myLocation = myLocation,
                    onGrantClick = {},
                    onLocationSettingsClick = {},
                    onVehiclesClick = {},
                    onMyLocationClick = onMyLocation,
                    onResetBearing = {},
                )
            }
        }
        return cameraPositionState
    }

    /**
     * Without precise location the map draws no blue dot, so the button would only be able to
     * report that it found nothing.
     */
    @Test
    fun `the my location button needs the blue dot it points at`() {
        setContent(accuracy = LocationAccuracy.COARSE)

        composeRule.onNodeWithContentDescription(GO_TO_MY_LOCATION).assertDoesNotExist()
    }

    /** With no saved spot the map is not even composed, so there is nothing to recentre. */
    @Test
    fun `without a map there are no map tools`() {
        setContent(accuracy = LocationAccuracy.PRECISE, hasLocation = false)

        composeRule.onNodeWithContentDescription(GO_TO_MY_LOCATION).assertDoesNotExist()
    }

    @Test
    fun `the my location button takes the user to the blue dot`() {
        var clicks = 0
        setContent(accuracy = LocationAccuracy.PRECISE, onMyLocation = { clicks++ })

        composeRule.onNodeWithContentDescription(GO_TO_MY_LOCATION).assertIsDisplayed().performClick()

        assertEquals(1, clicks)
    }

    /**
     * Once the camera is on the blue dot the button would only send the map where it already is,
     * so it hides itself. Panning away brings it back.
     */
    @Test
    fun `the my location button hides itself once the map arrives at the blue dot`() {
        val camera =
            setContent(
                accuracy = LocationAccuracy.PRECISE,
                myLocation = myPosition,
                cameraTarget = carSpot,
            )

        composeRule.onNodeWithContentDescription(GO_TO_MY_LOCATION).assertIsDisplayed()

        camera.position = CameraPosition.fromLatLngZoom(myPosition, 17f)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(GO_TO_MY_LOCATION).assertDoesNotExist()
    }

    /**
     * The compass used to sit at a fixed 92 dp from the top edge, which broke as soon as a banner
     * made the vehicle card taller. Both tools live in the column now.
     */
    @Test
    fun `the map tools stay below the vehicle card`() {
        setContent(accuracy = LocationAccuracy.PRECISE)

        val settings = composeRule.onNodeWithContentDescription("Vehicles").getUnclippedBoundsInRoot()
        val myLocation =
            composeRule.onNodeWithContentDescription(GO_TO_MY_LOCATION).getUnclippedBoundsInRoot()

        assertTrue(
            "The button should sit under the card: $myLocation, card bottom ${settings.bottom}",
            myLocation.top >= settings.bottom,
        )
    }

    /** An icon is the only label these buttons have; the tooltip is the label in words. */
    @Test
    fun `long pressing the my location button explains it`() {
        setContent(accuracy = LocationAccuracy.PRECISE)

        composeRule.onNodeWithContentDescription(GO_TO_MY_LOCATION).performTouchInput { longClick() }

        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithText(GO_TO_MY_LOCATION).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val GO_TO_MY_LOCATION = "Go to my location"
    }
}
