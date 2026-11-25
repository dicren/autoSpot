package com.dicren.autospot.ui.parking

import com.dicren.autospot.data.ParkingLocationEntity
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow

/** Edge of one Web Mercator tile: the unit the Google Maps zoom levels are built on. */
private const val TILE_SIZE_PX = 256.0

private const val DEGREES_PER_TURN = 360.0

/** The zoom a single parking spot is framed at; street level, where the car is recognisable. */
internal const val SPOT_ZOOM = 17f

/** Room left around a group of spots so its markers are not cut by the edges of the screen. */
internal const val OVERVIEW_PADDING_DP = 48

/**
 * Distance in screen pixels between [marker] and the centre of the map when the camera looks at
 * [target] with [zoom].
 *
 * The camera target is by definition the middle of the viewport, so measuring from there is
 * measuring from the centre of the screen. The Web Mercator scale is used instead of
 * `CameraPositionState.projection` because the projection needs a laid-out `GoogleMap`: it is null
 * before the first frame and does not exist in tests, while the camera position always does. Tilt
 * is ignored on purpose: it moves a point up or down the screen, not away from the middle.
 */
internal fun markerDriftPx(
    zoom: Float,
    target: LatLng,
    marker: LatLng,
): Double {
    val worldWidthPx = TILE_SIZE_PX * 2.0.pow(zoom.toDouble())
    val pxPerDegree = worldWidthPx / DEGREES_PER_TURN
    val dx = (target.longitude - marker.longitude) * pxPerDegree
    // A degree of latitude covers 1/cos(latitude) more pixels than one of longitude: the Mercator
    // projection stretches the map towards the poles.
    val dy = (target.latitude - marker.latitude) * pxPerDegree / cos(Math.toRadians(marker.latitude))
    return hypot(dx, dy)
}

/**
 * Whether the recenter button should be on screen, given the last camera sample and what it showed
 * before.
 *
 * The answer only changes while the camera is at rest. A moving map keeps the previous answer, so
 * the button cannot pop up halfway through a gesture that started on the spot, and cannot vanish
 * under the finger of a user who is already far away and still moving. Once the map settles, the
 * distance decides: inside [deadZonePx] the marker is centred enough and the button is pointless.
 */
internal fun recenterButtonVisibility(
    wasVisible: Boolean,
    isMoving: Boolean,
    driftPx: Double,
    deadZonePx: Float,
): Boolean = if (isMoving) wasVisible else driftPx > deadZonePx

/** Bounds that contain every [spot], or null when there is none. */
internal fun spotBounds(spots: List<ParkingLocationEntity>): LatLngBounds? {
    if (spots.isEmpty()) return null
    val builder = LatLngBounds.builder()
    spots.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
    return builder.build()
}

/**
 * The camera move that shows every [spot]: a group is framed with [paddingPx] to spare, while a
 * single spot is framed at [SPOT_ZOOM], which is the zoom the card has always used for a spot.
 *
 * Fitting a one-point bounds would ask for the maximum zoom, which is a street corner rather than
 * the car.
 */
internal fun cameraUpdateFor(
    spots: List<ParkingLocationEntity>,
    paddingPx: Int,
): CameraUpdate? {
    val single = spots.singleOrNull()
    if (single != null) {
        return CameraUpdateFactory.newLatLngZoom(LatLng(single.latitude, single.longitude), SPOT_ZOOM)
    }
    val bounds = spotBounds(spots) ?: return null
    // Spots saved at the same coordinate have no extent to fit, and an empty bounds asks for the
    // maximum zoom: a group like that needs the street-level zoom of a single spot instead.
    if (bounds.northeast == bounds.southwest) {
        return CameraUpdateFactory.newLatLngZoom(bounds.center, SPOT_ZOOM)
    }
    return CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)
}
