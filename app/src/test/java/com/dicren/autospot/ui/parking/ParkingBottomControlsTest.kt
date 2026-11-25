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

    private fun setContent(
        isShowingHistory: Boolean,
        canShowOlder: Boolean = true,
        canShowNewer: Boolean = true,
        onOlder: () -> Unit = {},
        onNewer: () -> Unit = {},
        onHistory: () -> Unit = {},
    ) {
        composeRule.setContent {
            AutoSpotTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ParkingBottomControls(
                        snackbarHostState = SnackbarHostState(),
                        location = location,
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
                                onCenterMap = {},
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
    }

    /**
     * The FAB used to be an overlay with a fixed bottom padding, so the card covered it as soon as
     * the card grew. Both live in the same column now; this keeps them from drifting apart again.
     */
    @Test
    fun `the navigation button never overlaps the location card`() {
        setContent(isShowingHistory = false)

        assertFabClearsCard()
    }

    @Test
    fun `an old spot does not grow the card over the navigation button`() {
        setContent(isShowingHistory = true)

        composeRule.onNodeWithContentDescription("Back to latest").assertIsDisplayed()

        assertFabClearsCard()
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
    private fun assertFabClearsCard() {
        val fab = composeRule.onNodeWithContentDescription(NAVIGATE).getUnclippedBoundsInRoot()
        val cardTop = composeRule.onNodeWithText("Parked").getUnclippedBoundsInRoot().top - 16.dp

        assertTrue("The FAB overlaps the card: $fab, card top $cardTop", fab.bottom + 8.dp <= cardTop)
    }

    private companion object {
        const val NAVIGATE = "Navigate with Google Maps"

        /** Far enough to pass the 72 dp threshold at any density Robolectric may pick. */
        const val LEFT = -600f
        const val RIGHT = 600f
    }
}
