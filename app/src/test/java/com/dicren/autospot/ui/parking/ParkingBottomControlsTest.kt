package com.dicren.autospot.ui.parking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.ui.theme.AutoSpotTheme
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
class ParkingBottomControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val formatter =
        ParkingTimeFormatter(
            ApplicationProvider.getApplicationContext<android.content.Context>().resources,
        )

    private val location =
        ParkingLocationEntity(
            id = 10,
            vehicleId = 1,
            latitude = 42.0,
            longitude = -5.0,
            timestamp = System.currentTimeMillis() - 5 * 60_000L,
        )

    private val spot = LatLng(location.latitude, location.longitude)

    /**
     * Renders the bottom overlay with the camera sitting on the displayed spot, and returns the
     * camera state so a test can move it (a pan) and see what the overlay does.
     */
    private fun setContent(
        isShowingHistory: Boolean,
        canShowOlder: Boolean = true,
        canShowNewer: Boolean = true,
        onOlder: () -> Unit = {},
        onNewer: () -> Unit = {},
        onHistory: () -> Unit = {},
        onRecenter: () -> Unit = {},
    ): CameraPositionState {
        lateinit var cameraPositionState: CameraPositionState
        composeRule.setContent {
            AutoSpotTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    cameraPositionState =
                        rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(spot, 17f)
                        }
                    ParkingBottomControls(
                        snackbarHostState = SnackbarHostState(),
                        location = location,
                        cameraPositionState = cameraPositionState,
                        timeFormatter = formatter,
                        nowMillis = location.timestamp + 5 * 60_000L,
                        historyCount = 3,
                        isShowingHistory = isShowingHistory,
                        canShowOlder = canShowOlder,
                        canShowNewer = canShowNewer,
                        callbacks =
                            ParkingBottomCallbacks(
                                onNavigate = {},
                                onShare = {},
                                onRecenter = { onRecenter() },
                                onHistory = onHistory,
                                onBackToLatest = {},
                                onOlder = onOlder,
                                onNewer = onNewer,
                            ),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        return cameraPositionState
    }

    /**
     * The FAB used to be an overlay with a fixed bottom padding, so the card covered it as soon as
     * the card grew. Both live in the same column now; this keeps them from drifting apart again.
     */
    @Test
    fun `the navigation button never overlaps the location card`() {
        setContent(isShowingHistory = false)

        assertFabClearsCard(NAVIGATE)
    }

    @Test
    fun `an old spot does not grow the card over the navigation button`() {
        setContent(isShowingHistory = true)

        composeRule.onNodeWithContentDescription("Back to latest").assertIsDisplayed()

        assertFabClearsCard(NAVIGATE)
    }

    /**
     * The card is where the address lives; the map button jumped out of it so the title keeps its
     * width. It has to stay out: a fourth icon is what squeezed the card.
     */
    @Test
    fun `the location card no longer carries the map button`() {
        setContent(isShowingHistory = true)

        composeRule.onNodeWithContentDescription(GO_TO_CAR).assertDoesNotExist()
    }

    @Test
    fun `a centered map does not offer to recenter`() {
        setContent(isShowingHistory = false)

        composeRule.onNodeWithContentDescription(GO_TO_CAR).assertDoesNotExist()
    }

    @Test
    fun `panning away from the spot offers to recenter`() {
        var recentered = 0
        val camera = setContent(isShowingHistory = false, onRecenter = { recentered++ })

        // A couple of kilometres away at zoom 17: the spot is well off the middle of the screen.
        camera.position = CameraPosition.fromLatLngZoom(LatLng(42.02, -5.0), 17f)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(GO_TO_CAR).assertIsDisplayed().performClick()

        assertEquals(1, recentered)
    }

    @Test
    fun `a negligible drift keeps the recenter button hidden`() {
        val camera = setContent(isShowingHistory = false)

        // About five metres: inside the dead zone around the middle of the screen.
        camera.position = CameraPosition.fromLatLngZoom(LatLng(42.00005, -5.0), 17f)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(GO_TO_CAR).assertDoesNotExist()
    }

    @Test
    fun `the recenter button appears above the navigation one and clear of the card`() {
        val camera = setContent(isShowingHistory = true)

        camera.position = CameraPosition.fromLatLngZoom(LatLng(42.02, -5.0), 17f)
        composeRule.waitForIdle()

        assertFabClearsCard(NAVIGATE)
        assertFabClearsCard(GO_TO_CAR)

        val recenter = composeRule.onNodeWithContentDescription(GO_TO_CAR).getUnclippedBoundsInRoot()
        val navigate = composeRule.onNodeWithContentDescription(NAVIGATE).getUnclippedBoundsInRoot()
        assertTrue(
            "The recenter button should sit above the route button: $recenter, $navigate",
            recenter.bottom <= navigate.top,
        )
    }

    @Test
    fun `swiping the card left shows the older spot`() {
        var older = 0
        setContent(isShowingHistory = true, onOlder = { older++ })

        swipeCard(LEFT)

        composeRule.waitUntil(timeoutMillis = 2_000) { older == 1 }
    }

    @Test
    fun `swiping the card right comes back towards the newest spot`() {
        var newer = 0
        setContent(isShowingHistory = true, onNewer = { newer++ })

        swipeCard(RIGHT)

        composeRule.waitUntil(timeoutMillis = 2_000) { newer == 1 }
    }

    @Test
    fun `the card does not step past the oldest spot`() {
        var older = 0
        setContent(isShowingHistory = true, canShowOlder = false, onOlder = { older++ })

        swipeCard(LEFT)
        composeRule.waitForIdle()

        assertEquals(0, older)
    }

    @Test
    fun `the card does not step past the newest spot`() {
        var newer = 0
        setContent(isShowingHistory = false, canShowNewer = false, onNewer = { newer++ })

        swipeCard(RIGHT)
        composeRule.waitForIdle()

        assertEquals(0, newer)
    }

    @Test
    fun `the card buttons still work inside the swipe area`() {
        var opened = 0
        setContent(isShowingHistory = true, onHistory = { opened++ })

        composeRule.onNodeWithContentDescription("History").performClick()

        assertEquals(1, opened)
    }

    /**
     * Swipes the card by hand instead of with `swipeLeft()`: that helper only travels the width of
     * the text node, which is less than the 72 dp the card has to move to count as a step.
     */
    private fun swipeCard(distancePx: Float) {
        composeRule.onNodeWithText("Parked").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(distancePx, 0f),
                durationMillis = 100,
            )
        }
    }

    /** The title sits 16 dp below the card's top edge, which is the card's own padding. */
    private fun assertFabClearsCard(description: String) {
        val fab = composeRule.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()
        val cardTop = composeRule.onNodeWithText("Parked").getUnclippedBoundsInRoot().top - 16.dp

        assertTrue("The FAB overlaps the card: $fab, card top $cardTop", fab.bottom + 8.dp <= cardTop)
    }

    private companion object {
        const val NAVIGATE = "Navigate with Google Maps"
        const val GO_TO_CAR = "Go to my car"

        /** Far enough to pass the 72 dp threshold at any density Robolectric may pick. */
        const val LEFT = -600f
        const val RIGHT = 600f
    }
}
