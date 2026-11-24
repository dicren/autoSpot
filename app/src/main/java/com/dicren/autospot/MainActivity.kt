package com.dicren.autospot

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                // null means "not read yet": show neither the map nor onboarding until DataStore
                // answers, instead of flashing the wrong screen on first launch.
                val onboardingCompleted by container.settingsRepository.isOnboardingCompleted
                    .collectAsStateWithLifecycle(initialValue = null)
                val coroutineScope = rememberCoroutineScope()
                var permissionsRequested by rememberSaveable { mutableStateOf(false) }

                // Ask only once the user knows what the app does. The former launch-time request
                // showed a system dialog before the first onboarding page.
                LaunchedEffect(onboardingCompleted) {
                    if (onboardingCompleted == true && !permissionsRequested) {
                        permissionsRequested = true
                        requestPermissions()
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
        }
    }

    private fun requestPermissions() {
        val missing =
            buildList {
                if (!Permissions.hasLocation(this@MainActivity)) {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                if (!Permissions.hasBluetoothConnect(this@MainActivity)) {
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }

        if (missing.isNotEmpty()) {
            permissionRequest.launch(missing.toTypedArray())
        }
    }
}
