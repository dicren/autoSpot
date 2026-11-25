package com.dicren.autospot.baselineprofile

import android.Manifest
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Generates the startup baseline profile on a connected device or emulator.
 *
 * Run `./gradlew :app:generateBaselineProfile`; the profile is copied into
 * `app/src/release/generated/baselineProfiles/`.
 *
 * A fresh install opens onboarding, so the first launch only profiles that flow. The generator
 * grants the runtime permissions and skips onboarding, then launches the parking screen again so
 * the returning-user path is covered too. The permission grant runs before the measurement
 * because the new permission flow asks for missing grants right after onboarding.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    private val baselineProfile = BaselineProfileRule()

    @get:Rule
    val rules: RuleChain =
        RuleChain
            .outerRule(
                GrantPermissionRule.grant(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ),
            ).around(baselineProfile)

    @Test
    fun startup() =
        baselineProfile.collect(
            packageName = "com.dicren.autospot",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            // The first launch shows onboarding; skip it and measure the parking screen too.
            // `testTagsAsResourceId` exposes the Compose test tag, so this does not depend on the
            // device locale.
            val skip =
                device.findObject(By.res("onboarding_skip"))
                    ?: device.findObject(By.text("Skip"))
                    ?: device.findObject(By.text("Omitir"))
            if (skip != null) {
                skip.click()
                device.waitForIdle()
                startActivityAndWait()
            }
        }
}
