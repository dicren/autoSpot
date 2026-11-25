package com.dicren.autospot.ui.parking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dicren.autospot.R
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.ui.components.IconTooltip
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.ui.parking.components.LocationCard
import com.dicren.autospot.ui.parking.components.LocationServicesBanner
import com.dicren.autospot.ui.parking.components.PermissionBanner
import com.dicren.autospot.ui.parking.components.VehicleCard
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The banners, the active-vehicle card and the map tools, all in one top column.
 *
 * The tools sit *under* the card instead of at a fixed offset from the top edge: the banners make
 * the card taller, and a fixed offset would eventually drop the buttons on top of it. It is the
 * same rule the bottom column follows.
 */
@Composable
internal fun ParkingTopOverlay(
    state: ParkingUiState,
    showBackgroundRationale: Boolean,
    cameraPositionState: CameraPositionState,
    /** Last known position of the user (the blue dot), or null while it is still unknown. */
    myLocation: LatLng?,
    onGrantClick: () -> Unit,
    onLocationSettingsClick: () -> Unit,
    onVehiclesClick: () -> Unit,
    onMyLocationClick: () -> Unit,
    onResetBearing: () -> Unit,
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

        // No saved spot, no map: the screen shows the empty-state card and there is no camera to
        // move, so the tools would only be able to do nothing.
        if (state.displayLocation != null) {
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Without precise location there is no blue dot to fly to, and the button would
                // only report that it found nothing.
                if (state.hasLocationPermission) {
                    // Until the first fix arrives the position is unknown, and showing the button
                    // is what lets the user ask for it. Once it is known the button is only worth
                    // having while the camera is somewhere else: sitting on the dot, it would do
                    // nothing but hide itself again.
                    val isAwayFromMe =
                        if (myLocation == null) {
                            true
                        } else {
                            rememberIsOffCenter(cameraPositionState, myLocation)
                        }
                    AnimatedVisibility(
                        visible = isAwayFromMe,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        MyLocationFab(onClick = onMyLocationClick)
                    }
                }
                CompassFab(cameraPositionState = cameraPositionState, onReset = onResetBearing)
            }
        }
    }
}

/**
 * The map tools share one look: small, surface-coloured and quiet, so they read as part of the map
 * instead of competing with the primary action (the route button) and the location card. Every one
 * of them is icon-only, so each carries a tooltip.
 */
@Composable
private fun MapToolFab(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconTooltip(text = contentDescription, modifier = modifier) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
            )
        }
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
        MapToolFab(
            imageVector = Icons.Default.Explore,
            contentDescription = stringResource(R.string.reset_orientation),
            onClick = onReset,
        )
    }
}

/**
 * How much [point] has to leave the middle of the screen before a "go back to it" button appears.
 *
 * A dead zone instead of "any movement at all" keeps a finger twitch, a zoom adjustment or the
 * last millimetre of an animation from flashing a button the user did not ask for. 24 dp is about
 * the space a marker pin already covers around its own tip.
 */
private val RECENTER_DEAD_ZONE = 24.dp

/**
 * True while [point] is not in the middle of the map any more: the camera has been panned away from
 * it and has come to rest there. Used for the saved spot and for the user's own position.
 *
 * The value is latched: it is only recomputed while the map is still, so a gesture that starts on
 * the point never summons a button mid-drag, and a button that is already showing does not vanish
 * under the finger of a user who is still moving. [snapshotFlow] re-evaluates this on camera
 * changes and the caller recomposes only when the answer flips, not on every frame of a gesture.
 * Zoom 0 is the untouched default position of the map, where the target is not a real place yet.
 */
@Composable
internal fun rememberIsOffCenter(
    cameraPositionState: CameraPositionState,
    point: LatLng,
): Boolean {
    val deadZonePx = with(LocalDensity.current) { RECENTER_DEAD_ZONE.toPx() }
    var isOffCenter by remember(cameraPositionState, point) { mutableStateOf(false) }
    LaunchedEffect(cameraPositionState, point, deadZonePx) {
        snapshotFlow {
            val camera = cameraPositionState.position
            val placed = camera.zoom > 0f
            val moving = cameraPositionState.isMoving || !placed
            val drift = if (placed) markerDriftPx(camera.zoom, camera.target, point) else 0.0
            moving to drift
        }.collect { (moving, drift) ->
            isOffCenter =
                recenterButtonVisibility(
                    wasVisible = isOffCenter,
                    isMoving = moving,
                    driftPx = drift,
                    deadZonePx = deadZonePx,
                )
        }
    }
    return isOffCenter
}

/**
 * Brings the camera back to the marked spot. Stateless: the caller decides when to show it.
 *
 * The car is what it points at, and the arrow on the route button (next to it) is what *drives*
 * there, so the two glyphs cannot be confused.
 */
@Composable
internal fun RecenterFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MapToolFab(
        imageVector = Icons.Default.DirectionsCar,
        contentDescription = stringResource(R.string.go_to_my_car),
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * The user's own position, the blue dot. Deliberately a different glyph from [RecenterFab]: two
 * crosshairs, or two cars, would be interchangeable.
 */
@Composable
internal fun MyLocationFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MapToolFab(
        imageVector = Icons.Default.MyLocation,
        contentDescription = stringResource(R.string.go_to_my_location),
        onClick = onClick,
        modifier = modifier,
    )
}

/** One-tap navigation to the displayed spot. */
@Composable
internal fun NavigateFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.navigate_button)
    IconTooltip(text = label, modifier = modifier) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** The actions of the bottom overlay, taking the spot they refer to. */
internal data class ParkingBottomCallbacks(
    val onNavigate: (ParkingLocationEntity) -> Unit,
    val onShare: (ParkingLocationEntity) -> Unit,
    val onRecenter: (ParkingLocationEntity) -> Unit,
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
 * Everything anchored to the bottom of the screen: the snackbar, the recenter and navigation FABs
 * and the card with the displayed spot.
 *
 * They share one column on purpose. The navigation FAB used to be an independent overlay placed
 * with a fixed bottom padding, so the card covered it as soon as the card grew (the "back to
 * latest" row, a longer text, a bigger font). Inside the column the FABs always sit above the
 * card, whatever height the card needs, and the snackbar stacks above them instead of crossing
 * them. The recenter button comes and goes without moving the route button: it is inserted above
 * it, and its spacing lives inside the animation so nothing is left behind when it hides.
 *
 * The card is also the handle for the history: swiping it left shows the older spot and swiping it
 * right comes back towards the newest one. [canShowOlder] and [canShowNewer] only control the
 * overscroll bump; the screen refuses the steps that would leave the history.
 */
@Composable
internal fun ParkingBottomControls(
    snackbarHostState: SnackbarHostState,
    location: ParkingLocationEntity?,
    cameraPositionState: CameraPositionState,
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

            val markerPosition =
                remember(location.latitude, location.longitude) {
                    LatLng(location.latitude, location.longitude)
                }
            AnimatedVisibility(
                visible = rememberIsOffCenter(cameraPositionState, markerPosition),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    RecenterFab(onClick = { callbacks.onRecenter(location) })
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

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
