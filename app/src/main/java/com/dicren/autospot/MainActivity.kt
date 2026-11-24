package com.dicren.autospot

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dicren.autospot.di.AppContainer
import com.dicren.autospot.ui.OnboardingScreen
import com.dicren.autospot.ui.parking.ParkingScreen
import com.dicren.autospot.ui.theme.AutoSpotTheme
import com.dicren.autospot.util.Permissions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Background location is requested later, from ParkingScreen, after explaining why it is
    // needed: Android 11+ sends the user to a settings screen for that permission.
    private val permissionRequest =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { _ ->
            // The parking screen refreshes its own flags when it resumes.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AutoSpotApp).container

        setContent {
            AutoSpotTheme {
                AutoSpotRoot(
                    container = container,
                    onRequestPermissions = ::requestPermissions,
                )
            }
        }
    }

    private fun requestPermissions() {
        val missing = missingPermissions(this)
        if (missing.isNotEmpty()) {
            permissionRequest.launch(missing)
        }
    }
}

/**
 * The onboarding/permission gate, extracted from the activity so it can be tested without an
 * instrumented activity launch.
 */
@Composable
internal fun AutoSpotRoot(
    container: AppContainer,
    onRequestPermissions: () -> Unit,
) {
    // null means "not read yet": show neither the map nor onboarding until DataStore answers,
    // instead of flashing the wrong screen on first launch.
    val onboardingCompleted by container.settingsRepository.isOnboardingCompleted
        .collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()
    var permissionsRequested by rememberSaveable { mutableStateOf(false) }

    // Ask only once the user knows what the app does. The former launch-time request showed a
    // system dialog before the first onboarding page.
    LaunchedEffect(onboardingCompleted) {
        if (onboardingCompleted == true && !permissionsRequested) {
            permissionsRequested = true
            onRequestPermissions()
        }
    }

    when (onboardingCompleted) {
        null -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {}
        }

        true -> {
            ParkingScreen(modifier = Modifier.fillMaxSize())
        }

        false -> {
            OnboardingScreen(
                onFinish = {
                    coroutineScope.launch {
                        container.settingsRepository.setOnboardingCompleted()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The runtime permissions the app still needs before it can monitor Bluetooth. */
internal fun missingPermissions(context: Context): Array<String> =
    buildList {
        if (!Permissions.hasLocation(context)) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (!Permissions.hasBluetoothConnect(context)) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()
