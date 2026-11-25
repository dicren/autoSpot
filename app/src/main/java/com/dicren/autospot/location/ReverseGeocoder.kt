package com.dicren.autospot.location

/**
 * Turns coordinates into a human-readable address.
 *
 * The result distinguishes "there is no address here" from "I could not answer": the first one is
 * final and must not be retried, while the second one (no network, backend missing) is worth a
 * second attempt later.
 */
fun interface ReverseGeocoder {
    suspend fun lookup(
        latitude: Double,
        longitude: Double,
    ): AddressLookup
}

sealed interface AddressLookup {
    /**
     * A usable place name. [street] is the street line ("Calle Mayor 12"); [locality] is the town
     * or city. Both are raw components: the UI decides how to join them.
     */
    data class Found(
        val street: String?,
        val locality: String?,
    ) : AddressLookup

    /** The service answered and there is no address for those coordinates (sea, open country). */
    data object NotFound : AddressLookup

    /** The service could not answer right now. The lookup should be retried later. */
    data object Unavailable : AddressLookup
}
