package com.dicren.autospot.ui.parking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dicren.autospot.R
import com.dicren.autospot.ui.parking.components.LocationServicesBanner
import com.dicren.autospot.ui.parking.components.PermissionBanner
import com.dicren.autospot.ui.parking.components.VehicleCard
import com.google.maps.android.compose.CameraPositionState

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
