package com.dicren.autospot.ui.parking

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dicren.autospot.R
import com.dicren.autospot.data.ParkingLocationEntity
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberUpdatedMarkerState

/** One stored spot as a clusterable map item; [isLatest] is what draws the car glyph. */
internal data class SpotClusterItem(
    val id: Long,
    val vehicleId: Long,
    override val position: LatLng,
    val isLatest: Boolean,
) : ClusterItem {
    // The card is the place where a spot explains itself; tapping the marker opens it.
    override val title: String? = null

    override val snippet: String? = null

    override val zIndex: Float? = if (isLatest) 1f else 0f
}

/**
 * The map. It draws one marker in the normal view and a clustered overview of every spot of the
 * scope while [overviewSpots] is not null.
 *
 * Both modes live in the same `GoogleMap` on purpose: swapping the composable would recreate the
 * map, and the camera (the whole point of the overview) with it.
 *
 * @param location the spot the card is showing; ignored while the overview is open.
 * @param overviewSpots every spot of the overview scope, or null in the normal view.
 * @param latestSpotIds the newest spot of each vehicle, drawn as a car.
 * @param onSpotClick a marker was tapped; the screen shows its card.
 * @param onClusterClick a group was tapped; the screen shows its card and frames it.
 * @param onMapClick empty map tapped; the overview drops the card.
 */
@SuppressLint("MissingPermission") // The blue dot is only enabled when the permission is granted.
@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun ParkingMap(
    location: ParkingLocationEntity?,
    overviewSpots: List<ParkingLocationEntity>?,
    latestSpotIds: Set<Long>,
    isMyLocationEnabled: Boolean,
    cameraPositionState: CameraPositionState,
    onSpotClick: (Long) -> Unit,
    onClusterClick: (Set<Long>) -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties =
            MapProperties(
                // The Maps SDK throws SecurityException if the blue dot is enabled without the
                // location permission (e.g. after revoking it in Settings).
                isMyLocationEnabled = isMyLocationEnabled,
            ),
        uiSettings =
            MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                // The SDK compass is pinned to the top-left corner of the map view, which sits
                // under the status bar, and it duplicates CompassFab. The app's own button is the
                // only way back to the original rotation.
                compassEnabled = false,
            ),
        onMapClick = { onMapClick() },
    ) {
        if (overviewSpots == null) {
            location?.let {
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(it.latitude, it.longitude)),
                    title = stringResource(R.string.marker_title),
                )
            }
        } else {
            val items =
                remember(overviewSpots, latestSpotIds) {
                    overviewSpots.map { spot ->
                        SpotClusterItem(
                            id = spot.id,
                            vehicleId = spot.vehicleId,
                            position = LatLng(spot.latitude, spot.longitude),
                            isLatest = spot.id in latestSpotIds,
                        )
                    }
                }
            Clustering(
                items = items,
                onClusterClick = { cluster ->
                    onClusterClick(cluster.items.map { it.id }.toSet())
                    true
                },
                onClusterItemClick = { item ->
                    onSpotClick(item.id)
                    true
                },
                clusterContent = { cluster -> ClusterMarker(count = cluster.size) },
                clusterItemContent = { item -> SpotMarker(item) },
                // A round badge is anchored at its middle, not at the tip of a pin.
                clusterContentAnchor = Offset(0.5f, 0.5f),
                clusterItemContentAnchor = Offset(0.5f, 0.5f),
            )
        }
    }
}

/**
 * A single spot in the overview: a dot in its vehicle's colour, with the car glyph when it is that
 * vehicle's newest spot. The glyph is what makes "where is each car parked" readable at a glance.
 */
@Composable
private fun SpotMarker(item: SpotClusterItem) {
    val color = vehicleSpotColor(item.vehicleId)
    val size = if (item.isLatest) 30.dp else 20.dp
    Box(
        modifier =
            Modifier
                .size(size)
                .background(color = color, shape = CircleShape)
                .border(width = 2.dp, color = Color.White, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (item.isLatest) {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * A group of spots, with how many it holds. Neutral on purpose: a group can mix vehicles, and a
 * colour would claim it belongs to one of them.
 */
@Composable
private fun ClusterMarker(count: Int) {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)
                .border(width = 3.dp, color = Color.White, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
