package com.dicren.autospot.ui.parking

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dicren.autospot.R
import com.dicren.autospot.data.ParkingLocationEntity
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberUpdatedMarkerState

/**
 * The map with the current parking spot. Extracted from the screen so the screen file stays about
 * orchestration instead of map configuration.
 */
@SuppressLint("MissingPermission") // The blue dot is only enabled when the permission is granted.
@Composable
fun ParkingMap(
    location: ParkingLocationEntity,
    isMyLocationEnabled: Boolean,
    cameraPositionState: CameraPositionState,
    modifier: Modifier = Modifier,
) {
    val position = LatLng(location.latitude, location.longitude)

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
            ),
    ) {
        Marker(
            state = rememberUpdatedMarkerState(position = position),
            title = stringResource(R.string.marker_title),
        )
    }
}
