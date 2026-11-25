package io.github.tycho991.kombu

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Launches the real activity, as the launcher does; on a fresh install it shows the login screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsLoginScreenOnFirstStart() {
        // The stored login is read from disk asynchronously; the loading state comes first.
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Nextcloud address").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Nextcloud address").assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
