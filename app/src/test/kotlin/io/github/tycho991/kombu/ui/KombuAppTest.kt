package io.github.tycho991.kombu.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.tycho991.kombu.data.account.LogoutResult
import io.github.tycho991.kombu.data.recipes.SyncProblem
import io.github.tycho991.kombu.data.recipes.SyncStatus
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import io.github.tycho991.kombu.ui.theme.KombuTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
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

    private val status = MutableStateFlow(SyncStatus())
    private val sync = FakeSyncControl()

    private var back: OnBackPressedDispatcher? = null

    private fun show(accounts: FakeAccountService) {
        composeRule.setContent {
            back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            KombuTheme { KombuApp(accounts, status, sync) }
        }
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

    @Test
    fun syncNowStartsASync() {
        show(FakeAccountService(signedIn = testCredentials))

        composeRule.onNodeWithText("Sync now").performClick()

        assertEquals(listOf("syncNow"), sync.calls)
    }

    @Test
    fun signingInAgainToTheSameAccountReturnsToTheStartScreen() {
        val accounts = FakeAccountService(signedIn = testCredentials)
        status.value = SyncStatus(recipeCount = 3, problem = SyncProblem.CredentialsRejected)
        show(accounts)

        composeRule.onNodeWithText("Sign in again").performClick()
        composeRule.onNodeWithText("Nextcloud address").assertIsDisplayed()
        composeRule.onNodeWithText("https://cloud.example.com").assertIsDisplayed()
        composeRule.onAllNodesWithText("Sign in")[1].performClick()
        composeRule.waitUntil { accounts.reauthenticatedSessions.isNotEmpty() }
        status.value = SyncStatus(recipeCount = 3)
        accounts.completion.complete(NextcloudResult.Success(testCredentials.copy(appPassword = "new")))

        composeRule.waitUntil { composeRule.onAllNodesWithText("Your recipes").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("3 recipes on this device").assertIsDisplayed()
        assertEquals(emptyList<String>(), accounts.completedSessions.map { it.token })
    }

    @Test
    fun signingInAgainWithAnotherAccountExplainsIt() {
        val accounts = FakeAccountService(signedIn = testCredentials)
        status.value = SyncStatus(problem = SyncProblem.CredentialsRejected)
        show(accounts)

        composeRule.onNodeWithText("Sign in again").performClick()
        composeRule.onAllNodesWithText("Sign in")[1].performClick()
        composeRule.waitUntil { accounts.reauthenticatedSessions.isNotEmpty() }
        accounts.completion.complete(NextcloudResult.Failure(NextcloudError.DifferentAccount))

        composeRule.waitUntil {
            composeRule.onAllNodesWithText("different account", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(testCredentials, accounts.accountState.value)
    }

    @Test
    fun signingInAgainCanBeCancelled() {
        status.value = SyncStatus(problem = SyncProblem.CredentialsRejected)
        show(FakeAccountService(signedIn = testCredentials))

        composeRule.onNodeWithText("Sign in again").performClick()
        composeRule.onNodeWithText("Nextcloud address").assertIsDisplayed()
        composeRule.runOnUiThread { checkNotNull(back).onBackPressed() }

        composeRule.onNodeWithText("Your recipes").assertIsDisplayed()
    }
}
