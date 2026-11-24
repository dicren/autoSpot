package com.dicren.autospot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.dicren.autospot.data.LocationRepository
import com.dicren.autospot.ui.OnboardingScreen
import com.dicren.autospot.ui.ParkingScreen
import com.dicren.autospot.ui.theme.AutoSpotTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionsToRequest = mutableListOf<String>()

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            requestBackgroundLocationPermission()
        }
    }

    private val backgroundLocationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Background permission handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPermissions()

        setContent {
            AutoSpotTheme {
                val repository = remember { LocationRepository(this) }
                val isOnboardingCompleted by repository.isOnboardingCompleted.collectAsState(initial = true)
                val coroutineScope = rememberCoroutineScope()

                if (isOnboardingCompleted) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        ParkingScreen(
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                } else {
                    OnboardingScreen(
                        onFinish = {
                            coroutineScope.launch {
                                repository.setOnboardingCompleted()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
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
        } else {
            requestBackgroundLocationPermission()
        }
    }

    private fun requestBackgroundLocationPermission() {
        val backgroundPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
        if (backgroundPermission != PackageManager.PERMISSION_GRANTED) {
            backgroundLocationPermissionRequest.launch(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }
    }
}
