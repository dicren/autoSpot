package com.dicren.autospot.ui.parking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dicren.autospot.R
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.ui.parking.components.LocationCard
import com.dicren.autospot.ui.parking.components.LocationServicesBanner
import com.dicren.autospot.ui.parking.components.PermissionBanner
import com.dicren.autospot.ui.parking.components.VehicleCard
import com.google.maps.android.compose.CameraPositionState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** The banners and the active-vehicle card shown over the map. */
@Composable
internal fun ParkingTopOverlay(
    state: ParkingUiState,
    showBackgroundRationale: Boolean,
    onGrantClick: () -> Unit,
    onLocationSettingsClick: () -> Unit,
    onVehiclesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                ).padding(16.dp),
    ) {
        if (!state.isReady) return@Column

        // The rationale dialog already explains the missing background grant; showing the
        // generic banner at the same time would ask for the same thing twice.
        if (!state.hasMonitoringPermission && !showBackgroundRationale) {
            PermissionBanner(
                isApproximateOnly = state.isLocationApproximate,
                onGrantClick = onGrantClick,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!state.isLocationEnabled) {
            LocationServicesBanner(onSettingsClick = onLocationSettingsClick)
            Spacer(modifier = Modifier.height(8.dp))
        }

        VehicleCard(
            vehicle = state.activeVehicle,
            onSettingsClick = onVehiclesClick,
        )
    }
}

/**
 * Own composable so the camera position is only read here: reading it in the screen body would
 * recompose the whole screen (map included) while the user pans or rotates the map.
 */
@Composable
internal fun CompassFab(
    cameraPositionState: CameraPositionState,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bearing by remember(cameraPositionState) {
        derivedStateOf { cameraPositionState.position.bearing }
    }

    AnimatedVisibility(
        visible = bearing != 0f,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        FloatingActionButton(
            onClick = onReset,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = stringResource(R.string.reset_orientation),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** One-tap navigation to the displayed spot. */
@Composable
internal fun NavigateFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = stringResource(R.string.navigate_button),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** The actions of the bottom overlay, taking the spot they refer to. */
internal data class ParkingBottomCallbacks(
    val onNavigate: (ParkingLocationEntity) -> Unit,
    val onShare: (ParkingLocationEntity) -> Unit,
    val onCenterMap: (ParkingLocationEntity) -> Unit,
    val onHistory: () -> Unit,
    val onBackToLatest: () -> Unit,
    /** One swipe to the left, towards the older spot. */
    val onOlder: () -> Unit,
    /** One swipe to the right, back towards the newest spot. */
    val onNewer: () -> Unit,
)

/** Distance the card has to travel before releasing it steps through the history. */
private val SWIPE_STEP_THRESHOLD = 72.dp

/** A quick flick steps even when it travels less than [SWIPE_STEP_THRESHOLD]. */
private const val SWIPE_FLING_VELOCITY = 1_200f

/** Share of the drag kept when there is nothing on that side: the bump says "end of the history". */
private const val SWIPE_OVERSCROLL_RESISTANCE = 0.25f

private const val SWIPE_SNAP_BACK_MILLIS = 180
private const val SWIPE_OUT_MILLIS = 140
private const val SWIPE_IN_MILLIS = 180

/**
 * Everything anchored to the bottom of the screen: the snackbar, the navigation FAB and the card
 * with the displayed spot.
 *
 * They share one column on purpose. The FAB used to be an independent overlay placed with a fixed
 * bottom padding, so the card covered it as soon as the card grew (the "back to latest" row, a
 * longer text, a bigger font). Inside the column the FAB always sits above the card, whatever
 * height the card needs, and the snackbar stacks above the FAB instead of crossing it.
 *
 * The card is also the handle for the history: swiping it left shows the older spot and swiping it
 * right comes back towards the newest one. [canShowOlder] and [canShowNewer] only control the
 * overscroll bump; the screen refuses the steps that would leave the history.
 */
@Composable
internal fun ParkingBottomControls(
    snackbarHostState: SnackbarHostState,
    location: ParkingLocationEntity?,
    timeFormatter: ParkingTimeFormatter,
    nowMillis: Long,
    historyCount: Int,
    isShowingHistory: Boolean,
    canShowOlder: Boolean,
    canShowNewer: Boolean,
    callbacks: ParkingBottomCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                ).padding(16.dp),
        horizontalAlignment = Alignment.End,
    ) {
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.fillMaxWidth())

        if (location != null) {
            Spacer(modifier = Modifier.height(12.dp))

            NavigateFab(onClick = { callbacks.onNavigate(location) })

            Spacer(modifier = Modifier.height(16.dp))

            HistorySwipeContainer(
                canSwipeOlder = canShowOlder,
                canSwipeNewer = canShowNewer,
                onSwipeOlder = callbacks.onOlder,
                onSwipeNewer = callbacks.onNewer,
            ) {
                LocationCard(
                    location = location,
                    timeFormatter = timeFormatter,
                    nowMillis = nowMillis,
                    historyCount = historyCount,
                    isFromHistory = isShowingHistory,
                    onShareClick = { callbacks.onShare(location) },
                    onHistoryClick = callbacks.onHistory,
                    onBackToLatestClick = callbacks.onBackToLatest,
                    onCenterMapClick = { callbacks.onCenterMap(location) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Turns the location card into a horizontal carousel over the parking history.
 *
 * The gesture lives in this wrapper instead of [LocationCard] so the card stays a stateless
 * component, and the offset is applied to the card rather than to the wrapper that detects the
 * drag: moving the node that reads the pointer would drag the coordinate system the deltas are
 * measured in and the card would not follow the finger.
 *
 * A completed swipe animates the card out, swaps the displayed spot and slides the new one in, so
 * the change reads as one movement instead of the card's text changing under the finger. The
 * finger is ignored meanwhile; being able to grab the card half off-screen would be worse.
 */
@Composable
private fun HistorySwipeContainer(
    canSwipeOlder: Boolean,
    canSwipeNewer: Boolean,
    onSwipeOlder: () -> Unit,
    onSwipeNewer: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val threshold = with(density) { SWIPE_STEP_THRESHOLD.toPx() }
    var width by remember { mutableIntStateOf(0) }
    var isStepping by remember { mutableStateOf(false) }

    val dragState =
        rememberDraggableState { delta ->
            if (!isStepping) {
                // Returning to the middle is never resisted, even when the other side is closed:
                // otherwise a single spot would make the card feel stuck.
                val returning = offsetX.value * delta < 0f
                val allowed = if (delta < 0f) canSwipeOlder else canSwipeNewer
                val travel = if (allowed || returning) delta else delta * SWIPE_OVERSCROLL_RESISTANCE
                val edge = width.toFloat().coerceAtLeast(threshold)
                scope.launch {
                    offsetX.snapTo((offsetX.value + travel).coerceIn(-edge, edge))
                }
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .onSizeChanged { width = it.width }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        if (!isStepping) {
                            val moved = offsetX.value
                            val older =
                                if (abs(velocity) >= SWIPE_FLING_VELOCITY) velocity < 0f else moved < 0f
                            val allowed = if (older) canSwipeOlder else canSwipeNewer
                            val farEnough =
                                abs(moved) >= threshold || abs(velocity) >= SWIPE_FLING_VELOCITY
                            if (!allowed || !farEnough) {
                                scope.launch { offsetX.animateTo(0f, tween(SWIPE_SNAP_BACK_MILLIS)) }
                            } else {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                // The step runs on the composition scope, not on the pointer input
                                // one: the animation needs the frame clock, and a cancelled gesture
                                // must not leave the card half off-screen.
                                isStepping = true
                                scope.launch {
                                    try {
                                        val exit = width.toFloat().coerceAtLeast(threshold)
                                        offsetX.animateTo(
                                            targetValue = if (older) -exit else exit,
                                            animationSpec = tween(SWIPE_OUT_MILLIS),
                                        )
                                        if (older) onSwipeOlder() else onSwipeNewer()
                                        // The new spot must reach composition before it slides in.
                                        withFrameNanos { }
                                        offsetX.snapTo(if (older) exit else -exit)
                                        offsetX.animateTo(0f, tween(SWIPE_IN_MILLIS))
                                    } finally {
                                        isStepping = false
                                    }
                                }
                            }
                        }
                    },
                ),
    ) {
        Box(modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
            content()
        }
    }
}
