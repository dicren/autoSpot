package com.dicren.autospot.ui.parking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.service.BluetoothMonitorService
import com.dicren.autospot.util.Permissions
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState

private const val TAG = "ParkingEffects"

/** Re-reads permissions and the system location switch every time the screen resumes. */
@Composable
internal fun ParkingEnvironmentEffect(
    viewModel: ParkingViewModel,
    lifecycleOwner: LifecycleOwner,
) {
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshEnvironment()
        }
    }
}

/**
 * Starts the Bluetooth monitor only when there is something to monitor and every permission is
 * in place. Waiting for `isReady` avoids stopping the monitor the boot receiver just started on
 * the first composition, when every state value is still the default one.
 */
@Composable
internal fun ParkingMonitorEffect(
    state: ParkingUiState,
    context: Context,
) {
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* Notifications are optional: the services keep running without them. */ }

    LaunchedEffect(state.isReady, state.vehicles.isNotEmpty(), state.hasMonitoringPermission) {
        if (!state.isReady) return@LaunchedEffect
        val shouldMonitor = state.vehicles.isNotEmpty() && state.hasMonitoringPermission
        if (shouldMonitor) {
            try {
                context.startForegroundService(
                    Intent(context, BluetoothMonitorService::class.java),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not start the Bluetooth monitor", e)
            }
        } else {
            context.stopService(Intent(context, BluetoothMonitorService::class.java))
        }
    }

    // The ongoing notification is only visible with POST_NOTIFICATIONS on Android 13+.
    LaunchedEffect(state.vehicles.isNotEmpty(), state.hasMonitoringPermission) {
        if (state.vehicles.isNotEmpty() &&
            state.hasMonitoringPermission &&
            Permissions.needsNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Explain the "Allow all the time" requirement before Android sends the user to its settings
 * page. The fact that it was shown is persisted, so it is not repeated on every cold start.
 */
@Composable
internal fun ParkingBackgroundLocationEffect(
    state: ParkingUiState,
    viewModel: ParkingViewModel,
    onShowRationale: () -> Unit,
    onHideRationale: () -> Unit,
) {
    LaunchedEffect(
        state.isReady,
        state.hasLocationPermission,
        state.hasBackgroundLocationPermission,
        state.vehicles.isNotEmpty(),
    ) {
        if (state.needsBackgroundLocationRationale) {
            viewModel.markBackgroundRationaleShown()
            onShowRationale()
        }
    }

    // Coming back from the system settings page with the grant in place should close the
    // explanation instead of leaving it on screen.
    LaunchedEffect(state.hasBackgroundLocationPermission) {
        if (state.hasBackgroundLocationPermission) onHideRationale()
    }
}

/** Keeps the camera on the selected or latest parking spot. */
@Composable
internal fun ParkingCameraEffect(
    displayLocation: ParkingLocationEntity?,
    cameraPositionState: CameraPositionState,
) {
    LaunchedEffect(displayLocation?.id) {
        displayLocation?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 17f),
            )
        }
    }
}
