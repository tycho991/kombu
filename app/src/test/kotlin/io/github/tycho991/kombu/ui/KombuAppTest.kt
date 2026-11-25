package io.github.tycho991.kombu.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.tycho991.kombu.data.account.LogoutResult
import io.github.tycho991.kombu.ui.theme.KombuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The entry point follows the account state (spec nextcloud-account, "Entry point depends on login state"). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class KombuAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(accounts: FakeAccountService) {
        composeRule.setContent { KombuTheme { KombuApp(accounts) } }
    }

    @Test
    fun showsTheLoginScreenWhenSignedOut() {
        show(FakeAccountService())

        composeRule.onNodeWithText("Nextcloud address").assertIsDisplayed()
    }

    @Test
    fun showsTheStartScreenWhenSignedIn() {
        show(FakeAccountService(signedIn = testCredentials))

        composeRule.onNodeWithText("Your recipes").assertIsDisplayed()
    }

    @Test
    fun opensTheAccountAndReturns() {
        show(FakeAccountService(signedIn = testCredentials))

        composeRule.onNodeWithText("Account").performClick()
        composeRule.onNodeWithText("https://cloud.example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        composeRule.onNodeWithText("Your recipes").assertIsDisplayed()
    }

    @Test
    fun onlineLogoutReturnsToTheLoginScreen() {
        show(FakeAccountService(signedIn = testCredentials))

        logout()

        composeRule.onNodeWithText("Nextcloud address").assertIsDisplayed()
        composeRule.onNodeWithText("app password is still listed", substring = true).assertDoesNotExist()
    }

    @Test
    fun offlineLogoutShowsTheNotice() {
        show(FakeAccountService(signedIn = testCredentials).apply { logoutResult = LogoutResult.LOCAL_ONLY })

        logout()

        composeRule.onNodeWithText("app password is still listed", substring = true).assertIsDisplayed()
    }

    private fun logout() {
        composeRule.onNodeWithText("Account").performClick()
        composeRule.onNodeWithText("Sign out").performClick()
        composeRule.onAllNodesWithText("Sign out")[1].performClick()
        composeRule.waitForIdle()
    }
}
