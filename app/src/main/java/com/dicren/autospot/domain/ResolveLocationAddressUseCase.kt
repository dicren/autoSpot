package com.dicren.autospot.domain

import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.ParkingRepository
import com.dicren.autospot.location.AddressLookup
import com.dicren.autospot.location.ReverseGeocoder
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fills in the street name of a stored parking spot.
 *
 * It is deliberately separate from [SaveParkingLocationUseCase]: geocoding needs the network and
 * can take seconds, while saving the coordinates must never depend on it. A spot is stored first
 * and its address is resolved afterwards, so a failed lookup only leaves the coordinates on screen.
 */
interface ResolveLocationAddress {
    /**
     * Resolves [location] and stores the result. [AddressLookupOutcome.Unavailable] means the
     * geocoder could not answer and the row is still unresolved, so it can be retried later.
     */
    suspend fun resolve(location: ParkingLocationEntity): AddressLookupOutcome
}

enum class AddressLookupOutcome {
    /** The row was updated (with an address or with an explicit "nothing here"). */
    Resolved,

    /** The geocoder could not answer; the row must stay pending. */
    Unavailable,
}

class ResolveLocationAddressUseCase(
    private val parkingRepository: ParkingRepository,
    private val reverseGeocoder: ReverseGeocoder,
) : ResolveLocationAddress {
    override suspend fun resolve(location: ParkingLocationEntity): AddressLookupOutcome =
        try {
            if (location.addressResolved) return AddressLookupOutcome.Resolved

            when (val lookup = reverseGeocoder.lookup(location.latitude, location.longitude)) {
                is AddressLookup.Found -> {
                    parkingRepository.updateAddress(location.id, lookup.street, lookup.locality)
                    AddressLookupOutcome.Resolved
                }

                AddressLookup.NotFound -> {
                    // Remember the miss: the same coordinates will not resolve better another day.
                    parkingRepository.updateAddress(location.id, null, null)
                    AddressLookupOutcome.Resolved
                }

                AddressLookup.Unavailable -> {
                    AddressLookupOutcome.Unavailable
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Total like the save use case: a storage failure must not cancel the caller's
            // background loop, and the row simply stays pending.
            AddressLookupOutcome.Unavailable
        }
}
