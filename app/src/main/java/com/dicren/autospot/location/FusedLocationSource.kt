package com.dicren.autospot.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

/**
 * Play Services implementation of [LocationSource].
 *
 * `getCurrentLocation` can take much longer than expected, so it is bounded by a timeout and the
 * request is cancelled when it expires. Without this the foreground service (and its "saving
 * location" notification) could stay alive indefinitely.
 *
 * Any other failure is reported as "no fix" instead of an exception: the caller falls back to the
 * last known location, which is still better than losing the save. Cancellation is rethrown so the
 * service can shut down immediately.
 */
class FusedLocationSource(
    context: Context,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission") // Callers check the permission first.
    override suspend fun currentLocation(): LocationFix? {
        val cancellation = CancellationTokenSource()
        return try {
            withTimeout(timeoutMillis) {
                client
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                    .await()
                    ?.toFix()
            }
        } catch (e: TimeoutCancellationException) {
            cancellation.cancel()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            cancellation.cancel()
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastLocation(): LocationFix? =
        try {
            client.lastLocation.await()?.toFix()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun Location.toFix() =
        LocationFix(
            latitude = latitude,
            longitude = longitude,
            timeMillis = time,
            accuracyMeters = if (hasAccuracy()) accuracy else null,
        )

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}
