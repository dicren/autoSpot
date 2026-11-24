package com.dicren.autospot.util

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleNameTest {

    @Test
    fun `the user name wins`() {
        assertEquals("Mi coche", VehicleName.resolve("Mi coche", "Car Multimedia"))
    }

    @Test
    fun `a blank user name falls back to the bluetooth name`() {
        assertEquals("Car Multimedia", VehicleName.resolve("   ", "Car Multimedia"))
    }

    @Test
    fun `both names are trimmed`() {
        assertEquals("Car Multimedia", VehicleName.resolve("", "  Car Multimedia  "))
    }
}
