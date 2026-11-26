package io.github.tycho991.kombu.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The entry point follows the account state (spec nextcloud-account), and the signed-in screens form a back stack
 * with the recipe list as its root (task 4.1, design D1 of recipe-browse-edit).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class KombuAppTest {
    @get:Rule(order = 0)
    val apps = TestAppRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val status = MutableStateFlow(SyncStatus(lastSuccessAt = 1_000L))
    private var back: OnBackPressedDispatcher? = null

    private fun show(accounts: FakeAccountService, restoration: StateRestorationTester? = null): TestApp {
        val testApp = apps.create(accounts, status)
        val content = @androidx.compose.runtime.Composable {
            back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            KombuTheme { KombuApp(testApp.dependencies) }
        }
        if (restoration == null) composeRule.setContent(content) else restoration.setContent(content)
        return testApp
    }

    private fun pressBack() {
        composeRule.runOnUiThread { checkNotNull(back).onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun shows(text: String) {
        waitFor(text)
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun waitFor(text: String, substring: Boolean = false) = composeRule.waitUntil {
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun openAccount() = composeRule.onNodeWithContentDescription("Account").performClick()

    /** The bar shows the stored status once the list has loaded from the database. */
    private fun openSyncStatus() {
        waitFor("Synced", substring = true)
        composeRule.onNodeWithText("Synced", substring = true).performClick()
    }

    private fun openSignInAgainFromTheBar() {
        waitFor("Sign in again to keep syncing")
        composeRule.onNodeWithText("Sign in again to keep syncing").performClick()
        composeRule.onNodeWithText("Sign in again").performClick()
    }

    @Test
    fun showsTheLoginScreenWhenSignedOut() {
        show(FakeAccountService())

        shows("Nextcloud address")
    }

    @Test
    fun theRecipeListIsTheRootWhenSignedIn() {
        show(FakeAccountService(signedIn = testCredentials))

        shows("Recipes")
        assertFalse("back leaves the app at the root", checkNotNull(back).hasEnabledCallbacks())
    }

    @Test
    fun opensTheAccountAndReturns() {
        show(FakeAccountService(signedIn = testCredentials))

        openAccount()
        shows("https://cloud.example.com")
        composeRule.onNodeWithText("Back").performClick()
        shows("Recipes")

        openAccount()
        shows("https://cloud.example.com")
        pressBack()
        shows("Recipes")
    }

    @Test
    fun opensTheSyncStatusFromTheBarAndReturns() {
        show(FakeAccountService(signedIn = testCredentials))

        openSyncStatus()
        shows("Sync now")
        assertTrue(checkNotNull(back).hasEnabledCallbacks())
        pressBack()

        shows("Recipes")
        assertFalse(checkNotNull(back).hasEnabledCallbacks())
    }

    @Test
    fun theBackStackSurvivesRecreation() {
        val restoration = StateRestorationTester(composeRule)
        show(FakeAccountService(signedIn = testCredentials), restoration)
        openSyncStatus()
        shows("Sync now")

        restoration.emulateSavedInstanceStateRestore()

        shows("Sync now")
        pressBack()
        shows("Recipes")
    }

    @Test
    fun onlineLogoutReturnsToTheLoginScreen() {
        show(FakeAccountService(signedIn = testCredentials))

        logout()

        shows("Nextcloud address")
        composeRule.onNodeWithText("app password is still listed", substring = true).assertDoesNotExist()
    }

    @Test
    fun signingOutClearsTheBackStack() {
        val accounts = FakeAccountService(signedIn = testCredentials)
        show(accounts)

        logout()
        shows("Nextcloud address")
        composeRule.runOnUiThread { accounts.accountState.value = testCredentials }

        waitFor("Recipes")
        composeRule.onNodeWithText("https://cloud.example.com").assertDoesNotExist()
        assertFalse(checkNotNull(back).hasEnabledCallbacks())
    }

    @Test
    fun offlineLogoutShowsTheNotice() {
        show(FakeAccountService(signedIn = testCredentials).apply { logoutResult = LogoutResult.LOCAL_ONLY })

        logout()

        composeRule.onNodeWithText("app password is still listed", substring = true).assertIsDisplayed()
    }

    private fun logout() {
        openAccount()
        composeRule.onNodeWithText("Sign out").performClick()
        composeRule.onAllNodesWithText("Sign out")[1].performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun syncNowStartsASync() {
        val testApp = show(FakeAccountService(signedIn = testCredentials))

        openSyncStatus()
        composeRule.onNodeWithText("Sync now").performClick()

        assertEquals(listOf("syncNow"), testApp.sync.calls)
    }

    @Test
    fun signingInAgainToTheSameAccountReturnsToTheSyncStatus() {
        val accounts = FakeAccountService(signedIn = testCredentials)
        status.value = SyncStatus(recipeCount = 3, problem = SyncProblem.CredentialsRejected)
        show(accounts)

        openSignInAgainFromTheBar()
        shows("Nextcloud address")
        shows("https://cloud.example.com")
        composeRule.onAllNodesWithText("Sign in")[1].performClick()
        composeRule.waitUntil { accounts.reauthenticatedSessions.isNotEmpty() }
        status.value = SyncStatus(recipeCount = 3)
        accounts.completion.complete(NextcloudResult.Success(testCredentials.copy(appPassword = "new")))

        waitFor("3 recipes on this device")
        shows("Sync now")
        assertEquals(emptyList<String>(), accounts.completedSessions.map { it.token })
        pressBack()
        shows("Recipes")
    }

    @Test
    fun signingInAgainWithAnotherAccountExplainsIt() {
        val accounts = FakeAccountService(signedIn = testCredentials)
        status.value = SyncStatus(problem = SyncProblem.CredentialsRejected)
        show(accounts)

        openSignInAgainFromTheBar()
        composeRule.onAllNodesWithText("Sign in")[1].performClick()
        composeRule.waitUntil { accounts.reauthenticatedSessions.isNotEmpty() }
        accounts.completion.complete(NextcloudResult.Failure(NextcloudError.DifferentAccount))

        waitFor("different account", substring = true)
        assertEquals(testCredentials, accounts.accountState.value)
    }

    @Test
    fun signingInAgainCanBeCancelled() {
        status.value = SyncStatus(problem = SyncProblem.CredentialsRejected)
        show(FakeAccountService(signedIn = testCredentials))

        openSignInAgainFromTheBar()
        shows("Nextcloud address")
        pressBack()

        shows("Sign in again")
        pressBack()
        shows("Recipes")
    }
}
