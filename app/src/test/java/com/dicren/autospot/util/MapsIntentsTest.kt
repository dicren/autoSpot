package com.dicren.autospot.util

import android.content.ActivityNotFoundException
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapsIntentsTest {
    @Test
    fun `navigation uri carries walking mode`() {
        assertEquals(
            "google.navigation:q=42.5,-5.6&mode=w",
            MapsIntents.navigationUri(42.5, -5.6).toString(),
        )
    }

    @Test
    fun `navigation intent is pinned to Google Maps`() {
        val intent = MapsIntents.navigationIntent(42.5, -5.6)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("com.google.android.apps.maps", intent.`package`)
    }

    @Test
    fun `share text is a maps link`() {
        assertEquals(
            "https://maps.google.com/?q=42.5,-5.6",
            MapsIntents.shareText(42.5, -5.6),
        )
    }

    @Test
    fun `navigation without Maps returns false instead of throwing`() {
        val started =
            MapsIntents.startNavigation(42.5, -5.6) {
                throw ActivityNotFoundException("Google Maps is not installed")
            }

        assertFalse(started)
    }

    @Test
    fun `navigation refused by the system returns false instead of throwing`() {
        val started =
            MapsIntents.startNavigation(42.5, -5.6) {
                throw SecurityException("Google Maps is disabled")
            }

        assertFalse(started)
    }

    @Test
    fun `navigation with Maps returns true`() {
        var launched: Intent? = null

        val started = MapsIntents.startNavigation(42.5, -5.6) { launched = it }

        assertTrue(started)
        assertEquals("com.google.android.apps.maps", launched?.`package`)
    }
}
