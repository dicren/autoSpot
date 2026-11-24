package com.dicren.autospot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dicren.autospot.ui.OnboardingScreen
import com.dicren.autospot.ui.parking.ParkingScreen
import com.dicren.autospot.ui.theme.AutoSpotTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val permissionsToRequest = mutableListOf<String>()

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

        // Asking again on every rotation would re-prompt after a denial.
        if (savedInstanceState == null) {
            requestPermissions()
        }

        val container = (application as AutoSpotApp).container

        setContent {
            AutoSpotTheme {
                // null means "not read yet": show neither the map nor onboarding until DataStore
                // answers, instead of flashing the wrong screen on first launch.
                val onboardingCompleted by container.settingsRepository.isOnboardingCompleted
                    .collectAsStateWithLifecycle(initialValue = null)
                val coroutineScope = rememberCoroutineScope()

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
        permissionsToRequest.clear()

        // Location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Bluetooth permission (Android 12+)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionRequest.launch(permissionsToRequest.toTypedArray())
        }
    }
}
