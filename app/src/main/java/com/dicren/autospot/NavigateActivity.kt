package com.dicren.autospot

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.dicren.autospot.di.appContainer
import com.dicren.autospot.util.MapsIntents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class NavigateActivity : ComponentActivity() {
    companion object {
        private const val TAG = "NavigateActivity"
        const val EXTRA_VEHICLE_ID = "vehicle_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vehicleId = intent.getLongExtra(EXTRA_VEHICLE_ID, -1L)

        lifecycleScope.launch {
            try {
                val container = applicationContext.appContainer
                val location =
                    if (vehicleId > 0) {
                        container.parkingRepository.latestLocationOnce(vehicleId)
                    } else {
                        // Fallback to active vehicle if no specific vehicle ID
                        container.vehicleRepository
                            .getActiveVehicleOnce()
                            ?.let { container.parkingRepository.latestLocationOnce(it.id) }
                    }

                if (location != null) {
                    if (!MapsIntents.openNavigation(this@NavigateActivity, location.latitude, location.longitude)) {
                        Toast.makeText(this@NavigateActivity, R.string.maps_not_found, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@NavigateActivity, R.string.no_location_saved, Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A headless shortcut must never take the process down.
                Log.e(TAG, "Could not read the parking location", e)
                Toast.makeText(this@NavigateActivity, R.string.operation_failed, Toast.LENGTH_SHORT).show()
            } finally {
                finish()
            }
        }
    }
}
