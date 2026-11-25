package com.dicren.autospot.ui.parking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.ui.parking.components.HISTORY_HANDLE_TAG
import com.dicren.autospot.ui.parking.components.LOCATION_CARD_TAG
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
        historyCount: Int = 3,
        isHistorySheetOpen: Boolean = false,
        isOverviewing: Boolean = false,
        spot: ParkingLocationEntity? = location,
        onOlder: () -> Unit = {},
        onNewer: () -> Unit = {},
        onHistory: () -> Unit = {},
        onRecenter: () -> Unit = {},
        onShowAllSpots: () -> Unit = {},
    ): CameraPositionState {
        lateinit var cameraPositionState: CameraPositionState
        composeRule.setContent {
            AutoSpotTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    cameraPositionState =
                        rememberCameraPositionState {
                            position =
                                CameraPosition.fromLatLngZoom(
                                    spot?.let { LatLng(it.latitude, it.longitude) }
                                        ?: LatLng(location.latitude, location.longitude),
                                    17f,
                                )
                        }
                    ParkingBottomControls(
                        snackbarHostState = SnackbarHostState(),
                        location = spot,
                        cameraPositionState = cameraPositionState,
                        timeFormatter = formatter,
                        nowMillis = location.timestamp + 5 * 60_000L,
                        historyCount = historyCount,
                        isShowingHistory = isShowingHistory,
                        isHistorySheetOpen = isHistorySheetOpen,
                        canShowOlder = canShowOlder,
                        canShowNewer = canShowNewer,
                        isOverviewing = isOverviewing,
                        onShowAllSpots = onShowAllSpots,
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
        var opened = 0
        setContent(isShowingHistory = true, onOlder = { older++ }, onHistory = { opened++ })

        swipeCard(LEFT)

        composeRule.waitUntil(timeoutMillis = 2_000) { older == 1 }
        assertEquals("A horizontal swipe must not open the sheet", 0, opened)
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
    fun `swiping the card up opens the history sheet`() {
        var opened = 0
        setContent(isShowingHistory = false, onHistory = { opened++ })

        swipeCard(dy = UP)

        composeRule.waitUntil(timeoutMillis = 2_000) { opened == 1 }
    }

    /**
     * The sheet used to wait for the release, so the card sat at the end of the lift with nothing
     * happening. It now starts while the finger is still down, as soon as the lift crosses the
     * threshold.
     */
    @Test
    fun `the history opens mid-gesture without waiting for the release`() {
        var opened = 0
        setContent(isShowingHistory = false, onHistory = { opened++ })

        composeRule.onNodeWithText("Parked").performTouchInput {
            down(center)
            moveBy(Offset(0f, UP))
        }
        composeRule.waitForIdle()

        assertEquals("The sheet must not wait for the finger to lift", 1, opened)

        composeRule.onNodeWithText("Parked").performTouchInput { up() }
    }

    /**
     * The swipe is the only visible way in, so the sheet also has to stay reachable without a
     * gesture: the grabber exposes a semantics click that TalkBack and switch access can invoke.
     */
    @Test
    fun `the grabber offers the history to accessibility services`() {
        var opened = 0
        setContent(isShowingHistory = false, onHistory = { opened++ })

        composeRule
            .onNodeWithContentDescription("History")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, opened)
    }

    /** A tap on the card is still a tap: only a real drag may open the sheet. */
    @Test
    fun `tapping the card does not open the history`() {
        var opened = 0
        setContent(isShowingHistory = false, onHistory = { opened++ })

        composeRule.onNodeWithText("Parked").performClick()

        assertEquals(0, opened)
    }

    @Test
    fun `a single spot has no history to open`() {
        var opened = 0
        setContent(isShowingHistory = false, historyCount = 1, onHistory = { opened++ })

        composeRule.onNodeWithTag(HISTORY_HANDLE_TAG).assertDoesNotExist()

        swipeCard(dy = UP)
        composeRule.waitForIdle()

        assertEquals(0, opened)
    }

    /**
     * In the overview there is no single car to go back to, so the extent takes its place: the
     * car button would mean "the spot I am showing", which is not what the mode is about.
     */
    @Test
    fun `the overview replaces the car button with the full extent`() {
        var shown = 0
        setContent(
            isShowingHistory = false,
            isOverviewing = true,
            onShowAllSpots = { shown++ },
        )

        composeRule.onNodeWithContentDescription(GO_TO_CAR).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(SHOW_ALL).assertIsDisplayed().performClick()

        assertEquals(1, shown)
    }

    /** With no group picked there is no card, but the way back to the extent still matters. */
    @Test
    fun `the overview without a card still offers the full extent`() {
        setContent(isShowingHistory = false, isOverviewing = true, spot = null)

        composeRule.onNodeWithContentDescription(SHOW_ALL).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(NAVIGATE).assertDoesNotExist()
        composeRule.onNodeWithTag(LOCATION_CARD_TAG).assertDoesNotExist()
    }

    @Test
    fun `a focused group keeps the route button under the full extent one`() {
        setContent(isShowingHistory = false, isOverviewing = true)

        assertFabClearsCard(NAVIGATE)
        assertFabClearsCard(SHOW_ALL)

        val showAll = composeRule.onNodeWithContentDescription(SHOW_ALL).getUnclippedBoundsInRoot()
        val navigate = composeRule.onNodeWithContentDescription(NAVIGATE).getUnclippedBoundsInRoot()
        assertTrue(
            "The full extent button should sit above the route button: $showAll, $navigate",
            showAll.bottom <= navigate.top,
        )
    }

    /**
     * Swipes the card by hand instead of with `swipeLeft()`: that helper only travels the width of
     * the text node, which is less than the 72 dp the card has to move to count as a step.
     */
    private fun swipeCard(
        dx: Float = 0f,
        dy: Float = 0f,
    ) {
        composeRule.onNodeWithText("Parked").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(dx, dy),
                durationMillis = 100,
            )
        }
    }

    private fun assertFabClearsCard(description: String) {
        val fab = composeRule.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()
        val cardTop = composeRule.onNodeWithTag(LOCATION_CARD_TAG).getUnclippedBoundsInRoot().top

        assertTrue("The FAB overlaps the card: $fab, card top $cardTop", fab.bottom + 8.dp <= cardTop)
    }

    private companion object {
        const val NAVIGATE = "Navigate with Google Maps"
        const val GO_TO_CAR = "Go to my car"
        const val SHOW_ALL = "Show all spots"

        /** Far enough to pass the 72 dp (and 32 dp upward) threshold at any Robolectric density. */
        const val LEFT = -600f
        const val RIGHT = 600f
        const val UP = -600f
    }
}
