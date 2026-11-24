package com.dicren.autospot.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dicren.autospot.ui.theme.AutoSpotTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the first page explains the app`() {
        composeRule.setContent {
            AutoSpotTheme { OnboardingScreen(onFinish = {}) }
        }

        composeRule.onNodeWithText("Welcome to AutoSpot").assertIsDisplayed()
    }

    @Test
    fun `skip finishes onboarding`() {
        var finished = false
        composeRule.setContent {
            AutoSpotTheme { OnboardingScreen(onFinish = { finished = true }) }
        }

        composeRule.onNodeWithText("Skip").performClick()

        assertTrue(finished)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h480dp")
    fun `very small screens still show the page and the navigation`() {
        composeRule.setContent {
            AutoSpotTheme { OnboardingScreen(onFinish = {}) }
        }

        composeRule.onNodeWithText("Welcome to AutoSpot").assertIsDisplayed()
        composeRule.onNodeWithText("Next").assertIsDisplayed()
    }
}
