package com.dicren.autospot.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressFormatterTest {
    @Test
    fun `street and town are joined with a comma`() {
        assertEquals("Calle Mayor 12, Getafe", formatAddress("Calle Mayor 12", "Getafe"))
    }

    @Test
    fun `a missing town keeps the street alone`() {
        assertEquals("Calle Mayor 12", formatAddress("Calle Mayor 12", null))
    }

    @Test
    fun `a missing street keeps the town alone`() {
        assertEquals("Getafe", formatAddress(null, "Getafe"))
    }

    @Test
    fun `blank components are ignored`() {
        assertEquals("Getafe", formatAddress("   ", " Getafe "))
    }

    @Test
    fun `nothing usable formats to null so callers can fall back`() {
        assertNull(formatAddress(null, null))
        assertNull(formatAddress("", " "))
    }
}
