package com.dicren.autospot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.dicren.autospot.data.LocationRepository
import kotlinx.coroutines.runBlocking

class NavigateActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VEHICLE_ID = "vehicle_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = LocationRepository(applicationContext)
        val vehicleId = intent.getLongExtra(EXTRA_VEHICLE_ID, -1L)

        val location = runBlocking {
            if (vehicleId > 0) {
                repository.getLatestLocationByVehicleSync(vehicleId)
            } else {
                // Fallback to active vehicle if no specific vehicle ID
                val activeVehicle = repository.getActiveVehicleSync()
                activeVehicle?.let { repository.getLatestLocationByVehicleSync(it.id) }
            }
        }

        if (location != null) {
            val uri = Uri.parse("google.navigation:q=${location.latitude},${location.longitude}&mode=w")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.maps_not_found, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, R.string.no_location_saved, Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}
