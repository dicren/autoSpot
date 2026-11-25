package com.dicren.autospot.domain

import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.ParkingRepository
import com.dicren.autospot.location.AddressLookup
import com.dicren.autospot.location.ReverseGeocoder
import com.dicren.autospot.util.FakeParkingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveLocationAddressUseCaseTest {
    private val vehicleId = 1L

    private fun location(
        id: Long = 10,
        addressResolved: Boolean = false,
    ) = ParkingLocationEntity(
        id = id,
        vehicleId = vehicleId,
        latitude = 42.0,
        longitude = -5.0,
        timestamp = 100,
        addressResolved = addressResolved,
    )

    private fun repository(vararg locations: ParkingLocationEntity) =
        FakeParkingRepository(mutableMapOf(vehicleId to locations.toList()))

    private class RecordingGeocoder(
        private val result: AddressLookup,
    ) : ReverseGeocoder {
        var calls = 0
            private set

        override suspend fun lookup(
            latitude: Double,
            longitude: Double,
        ): AddressLookup {
            calls++
            return result
        }
    }

    private class FailingParkingRepository : ParkingRepository by FakeParkingRepository() {
        override suspend fun updateAddress(
            id: Long,
            street: String?,
            locality: String?,
        ) = throw IllegalStateException("database closed")
    }

    @Test
    fun `a found address is stored on the spot`() =
        runTest {
            val parking = repository(location())
            val geocoder = RecordingGeocoder(AddressLookup.Found("Calle Mayor 12", "Getafe"))

            val outcome = ResolveLocationAddressUseCase(parking, geocoder).resolve(location())

            assertEquals(AddressLookupOutcome.Resolved, outcome)
            val saved = parking.latestLocationOnce(vehicleId)!!
            assertEquals("Calle Mayor 12", saved.street)
            assertEquals("Getafe", saved.locality)
            assertTrue(saved.addressResolved)
        }

    @Test
    fun `coordinates with no address are marked resolved to avoid asking again`() =
        runTest {
            val parking = repository(location())
            val geocoder = RecordingGeocoder(AddressLookup.NotFound)

            val outcome = ResolveLocationAddressUseCase(parking, geocoder).resolve(location())

            assertEquals(AddressLookupOutcome.Resolved, outcome)
            val saved = parking.latestLocationOnce(vehicleId)!!
            assertTrue(saved.addressResolved)
            assertNull(saved.street)
        }

    @Test
    fun `a geocoder that cannot answer leaves the spot pending`() =
        runTest {
            val parking = repository(location())
            val geocoder = RecordingGeocoder(AddressLookup.Unavailable)

            val outcome = ResolveLocationAddressUseCase(parking, geocoder).resolve(location())

            assertEquals(AddressLookupOutcome.Unavailable, outcome)
            assertFalse(parking.latestLocationOnce(vehicleId)!!.addressResolved)
        }

    @Test
    fun `an already resolved spot is never geocoded again`() =
        runTest {
            val parked = location(addressResolved = true)
            val parking = repository(parked)
            val geocoder = RecordingGeocoder(AddressLookup.Found("Calle Mayor 12", "Getafe"))

            val outcome = ResolveLocationAddressUseCase(parking, geocoder).resolve(parked)

            assertEquals(AddressLookupOutcome.Resolved, outcome)
            assertEquals(0, geocoder.calls)
        }

    @Test
    fun `a storage failure is reported as unavailable instead of escaping`() =
        runTest {
            val geocoder = RecordingGeocoder(AddressLookup.Found("Calle Mayor 12", "Getafe"))
            val useCase = ResolveLocationAddressUseCase(FailingParkingRepository(), geocoder)

            assertEquals(AddressLookupOutcome.Unavailable, useCase.resolve(location()))
        }
}
