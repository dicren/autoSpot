package com.dicren.autospot.ui.parking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.location.LocationFix
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
    onStartFailed: () -> Unit,
) {
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* Notifications are optional: the services keep running without them. */ }
    // The effect outlives configuration changes, so the message must come from the latest
    // composition instead of the one captured when it started.
    val currentOnStartFailed by rememberUpdatedState(onStartFailed)

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
                currentOnStartFailed()
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
            // The flag is persisted from the dialog callbacks, not here: a dialog that never
            // reaches the user must not stop the explanation from appearing next time.
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

/**
 * Keeps the blue dot's last known position in the screen state.
 *
 * The "go to my location" button hides itself once the camera sits on that point, so the screen
 * needs the point itself, not just the tap. The lookup is the same one the button uses (last known
 * first) and runs on every resume: the user has usually moved since the last one. A failed lookup
 * keeps the previous value, which is still better than showing a button that cannot tell whether
 * it is needed.
 */
@Composable
internal fun ParkingMyLocationEffect(
    lifecycleOwner: LifecycleOwner,
    hasLocationPermission: Boolean,
    findMyLocation: suspend () -> LocationFix?,
    onLocation: (LocationFix) -> Unit,
) {
    // The effect outlives configuration changes; both lambdas must come from the latest one.
    val currentFind by rememberUpdatedState(findMyLocation)
    val currentOnLocation by rememberUpdatedState(onLocation)

    LaunchedEffect(lifecycleOwner, hasLocationPermission) {
        if (!hasLocationPermission) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            currentFind()?.let(currentOnLocation)
        }
    }
}
