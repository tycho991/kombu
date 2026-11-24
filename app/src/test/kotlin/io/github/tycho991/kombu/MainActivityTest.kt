package io.github.tycho991.kombu

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Launches the real activity, as the launcher does, and checks that the first screen appears. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsStartScreen() {
        composeRule.onNodeWithText("Your recipes").assertIsDisplayed()
    }
}
