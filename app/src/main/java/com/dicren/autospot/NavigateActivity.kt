package com.dicren.autospot

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.dicren.autospot.data.LocationRepository
import com.dicren.autospot.util.MapsIntents
import kotlinx.coroutines.launch

class NavigateActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VEHICLE_ID = "vehicle_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vehicleId = intent.getLongExtra(EXTRA_VEHICLE_ID, -1L)

        lifecycleScope.launch {
            val repository = LocationRepository(applicationContext)
            val location = if (vehicleId > 0) {
                repository.getLatestLocationByVehicleSync(vehicleId)
            } else {
                // Fallback to active vehicle if no specific vehicle ID
                repository.getActiveVehicleSync()
                    ?.let { repository.getLatestLocationByVehicleSync(it.id) }
            }

            if (location != null) {
                if (!MapsIntents.openNavigation(this@NavigateActivity, location.latitude, location.longitude)) {
                    Toast.makeText(this@NavigateActivity, R.string.maps_not_found, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@NavigateActivity, R.string.no_location_saved, Toast.LENGTH_SHORT).show()
            }

            finish()
        }
    }
}
