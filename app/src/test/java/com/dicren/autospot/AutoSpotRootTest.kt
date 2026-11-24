package com.dicren.autospot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dicren.autospot.ui.theme.AutoSpotTheme
import com.dicren.autospot.util.FakeSettingsRepository
import com.dicren.autospot.util.TestAppContainer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AutoSpotRootTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val app: AutoSpotApp = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        app.containerOverride = null
    }

    @Test
    fun `permissions are not requested before onboarding finishes`() {
        val settings = FakeSettingsRepository(onboardingCompleted = false)
        val container = TestAppContainer(settingsRepository = settings)
        app.containerOverride = container
        var requested = false

        composeRule.setContent {
            AutoSpotTheme {
                AutoSpotRoot(container) { requested = true }
            }
        }

        composeRule.onNodeWithText("Welcome to AutoSpot").assertIsDisplayed()
        assertFalse(requested)
    }

    @Test
    fun `permissions are requested once onboarding is complete`() {
        val settings = FakeSettingsRepository(onboardingCompleted = false)
        val container = TestAppContainer(settingsRepository = settings)
        app.containerOverride = container
        var requested = false

        composeRule.setContent {
            AutoSpotTheme {
                AutoSpotRoot(container) { requested = true }
            }
        }

        runBlocking { settings.setOnboardingCompleted() }

        composeRule.waitUntil(timeoutMillis = 5_000) { requested }
        assertTrue(requested)
    }

    @Test
    fun `a returning user gets the permission prompt on the first composition`() {
        val settings = FakeSettingsRepository(onboardingCompleted = true)
        val container = TestAppContainer(settingsRepository = settings)
        app.containerOverride = container
        var requested = false

        composeRule.setContent {
            AutoSpotTheme {
                AutoSpotRoot(container) { requested = true }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { requested }
        assertTrue(requested)
    }
}
