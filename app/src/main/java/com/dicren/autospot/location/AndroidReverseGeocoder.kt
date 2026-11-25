package com.dicren.autospot.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [ReverseGeocoder] backed by the platform [Geocoder].
 *
 * It needs no API key and no extra billing: the coordinates are resolved by the geocoding service
 * the device already has (Google's on Play devices). In exchange it requires network and can be
 * missing on some builds, so every outcome is best effort and the UI must work without it.
 */
class AndroidReverseGeocoder(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : ReverseGeocoder {
    private val appContext = context.applicationContext

    override suspend fun lookup(
        latitude: Double,
        longitude: Double,
    ): AddressLookup =
        withContext(ioDispatcher) {
            if (!Geocoder.isPresent()) return@withContext AddressLookup.Unavailable

            val addresses =
                try {
                    resolve(Geocoder(appContext), latitude, longitude)
                } catch (e: IOException) {
                    // No network, or the backend is unreachable. Worth retrying later.
                    Log.w(TAG, "Geocoder unavailable", e)
                    return@withContext AddressLookup.Unavailable
                } catch (e: IllegalArgumentException) {
                    // Invalid coordinates can never resolve; do not retry.
                    Log.w(TAG, "Invalid coordinates for reverse geocoding", e)
                    return@withContext AddressLookup.NotFound
                } catch (e: Exception) {
                    Log.w(TAG, "Reverse geocoding failed", e)
                    return@withContext AddressLookup.Unavailable
                }

            val address = addresses.firstOrNull() ?: return@withContext AddressLookup.NotFound
            val street = streetLine(address)
            val locality =
                listOfNotNull(address.locality, address.subAdminArea, address.adminArea)
                    .firstOrNull { it.isNotBlank() }

            if (street == null && locality == null) {
                // Sparse result: keep the full line instead of showing nothing.
                val line = address.getAddressLine(0)?.takeIf { it.isNotBlank() }
                if (line == null) AddressLookup.NotFound else AddressLookup.Found(line, null)
            } else {
                AddressLookup.Found(street, locality)
            }
        }

    /**
     * The blocking call is deprecated since API 33 in favour of the listener one. The listener
     * reports failures as a string, so they are surfaced as [IOException] (retryable) to keep one
     * error path for both implementations.
     */
    private suspend fun resolve(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(
                    latitude,
                    longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) continuation.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IOException(errorMessage ?: "Geocoder error"),
                                )
                            }
                        }
                    },
                )
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
        }

    /** "Calle Mayor 12": the street name plus the house number when the service reports one. */
    private fun streetLine(address: Address): String? =
        listOfNotNull(address.thoroughfare, address.subThoroughfare)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { null }

    private companion object {
        const val TAG = "AndroidReverseGeocoder"
    }
}
