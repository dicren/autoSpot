package com.dicren.autospot

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.dicren.autospot.data.LocationRepository
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
                val uri = "google.navigation:q=${location.latitude},${location.longitude}&mode=w".toUri()
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                try {
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this@NavigateActivity, R.string.maps_not_found, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@NavigateActivity, R.string.no_location_saved, Toast.LENGTH_SHORT).show()
            }

            finish()
        }
    }
}
