package com.dicren.autospot.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the startup baseline profile on a connected device or emulator.
 *
 * Run `./gradlew :app:generateBaselineProfile`; the profile is copied into
 * `app/src/release/generated/baselineProfiles/`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() =
        baselineProfileRule.collect(
            packageName = "com.dicren.autospot",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
        }
}
