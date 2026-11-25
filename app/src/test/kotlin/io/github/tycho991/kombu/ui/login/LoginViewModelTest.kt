package io.github.tycho991.kombu.ui.login

import androidx.lifecycle.SavedStateHandle
import io.github.tycho991.kombu.nextcloudauth.AddressError
import io.github.tycho991.kombu.nextcloudauth.LoginSession
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import io.github.tycho991.kombu.ui.FakeAccountService
import io.github.tycho991.kombu.ui.testCredentials
import io.github.tycho991.kombu.ui.testSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Dispatchers.setMain and UnconfinedTestDispatcher are the standard way to test viewModelScope.
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val accounts = FakeAccountService()
    private val savedState = SavedStateHandle()

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LoginViewModel(accounts, savedState)

    @Test
    fun showsAnAddressError() {
        accounts.startResult = NextcloudResult.Failure(NextcloudError.InvalidAddress(AddressError.CLEARTEXT))
        val viewModel = viewModel()

        viewModel.onAddressChange("http://cloud.example.com")
        viewModel.signIn()

        assertEquals(
            LoginUiState(
                address = "http://cloud.example.com",
                error = NextcloudError.InvalidAddress(AddressError.CLEARTEXT),
            ),
            viewModel.state.value,
        )
    }

    @Test
    fun showsEachServerCheckErrorAndKeepsTheAddress() {
        val errors =
            listOf(
                NextcloudError.Unreachable,
                NextcloudError.CertificateRejected,
                NextcloudError.NotNextcloud,
                NextcloudError.Maintenance,
                NextcloudError.ServerError(500),
            )
        errors.forEach { error ->
            accounts.startResult = NextcloudResult.Failure(error)
            val viewModel = viewModel()
            viewModel.onAddressChange("cloud.example.com")

            viewModel.signIn()

            assertEquals(LoginUiState(address = "cloud.example.com", error = error), viewModel.state.value)
        }
    }

    @Test
    fun editingTheAddressClearsTheError() {
        accounts.startResult = NextcloudResult.Failure(NextcloudError.Unreachable)
        val viewModel = viewModel()
        viewModel.signIn()

        viewModel.onAddressChange("cloud.example.com")

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun opensTheLoginPageAndWaits() = runTest {
        val viewModel = viewModel()
        viewModel.onAddressChange("cloud.example.com")

        viewModel.signIn()

        assertEquals(testSession.loginUrl.toString(), viewModel.loginPages.first())
        assertEquals(LoginStep.WAITING_FOR_APPROVAL, viewModel.state.value.step)
        assertEquals(listOf("cloud.example.com"), accounts.startInputs)
        assertEquals(listOf(testSession), accounts.completedSessions)
    }

    @Test
    fun resetsTheFormAfterSuccess() {
        val viewModel = viewModel()
        viewModel.onAddressChange("cloud.example.com")
        viewModel.signIn()

        accounts.completion.complete(NextcloudResult.Success(testCredentials))

        assertEquals(LoginUiState(address = "cloud.example.com"), viewModel.state.value)
        assertFalse(savedState.contains("login.token"))
    }

    @Test
    fun showsTimeoutAndCookbookErrors() {
        listOf(NextcloudError.LoginExpired, NextcloudError.CookbookMissing).forEach { error ->
            accounts.completion = CompletableDeferred()
            val viewModel = viewModel()
            viewModel.signIn()

            accounts.completion.complete(NextcloudResult.Failure(error))

            assertEquals(LoginUiState(error = error), viewModel.state.value)
            assertFalse(savedState.contains("login.token"))
        }
    }

    @Test
    fun cancelStopsWaitingAndStoresNothing() {
        val viewModel = viewModel()
        viewModel.onAddressChange("cloud.example.com")
        viewModel.signIn()

        viewModel.cancel()
        accounts.completion.complete(NextcloudResult.Success(testCredentials))

        assertEquals(LoginUiState(address = "cloud.example.com"), viewModel.state.value)
        assertFalse(savedState.contains("login.token"))
    }

    @Test
    fun ignoresSignInWhileBusy() {
        val viewModel = viewModel()
        viewModel.signIn()

        viewModel.signIn()

        assertEquals(1, accounts.startInputs.size)
    }

    @Test
    fun resumesPollingAfterProcessDeath() {
        val first = viewModel()
        first.onAddressChange("cloud.example.com")
        first.signIn()

        val restored = LoginViewModel(accounts, savedState)

        assertEquals(LoginStep.WAITING_FOR_APPROVAL, restored.state.value.step)
        assertEquals("cloud.example.com", restored.state.value.address)
        assertEquals(listOf(testSession, testSession), accounts.completedSessions)
    }

    @Test
    fun ignoresAnIncompleteSavedSession() {
        savedState["login.token"] = "poll-token"

        val viewModel = viewModel()

        assertEquals(LoginStep.ENTER_ADDRESS, viewModel.state.value.step)
        assertTrue(accounts.completedSessions.isEmpty())
    }

    @Test
    fun wakesThePollingOnResume() {
        val viewModel = viewModel()
        viewModel.signIn()

        viewModel.onResume()

        assertTrue(checkNotNull(accounts.lastWakeUps).tryReceive().isSuccess)
    }

    @Test
    fun reopensTheLoginPage() = runTest {
        val viewModel = viewModel()
        viewModel.signIn()
        viewModel.loginPages.first()

        viewModel.reopenLoginPage()

        assertEquals(testSession.loginUrl.toString(), viewModel.loginPages.first())
    }

    @Test
    fun reportsAMissingBrowser() {
        val viewModel = viewModel()
        viewModel.signIn()

        viewModel.onBrowserMissing()

        assertTrue(viewModel.state.value.browserMissing)
    }

    @Test
    fun signingInAgainStartsWithTheServerAndReplacesTheCredentials() = runTest {
        val viewModel = LoginViewModel(
            accounts,
            savedState,
            signInAgain = true,
            initialAddress = "https://cloud.example.com",
        )
        assertEquals("https://cloud.example.com", viewModel.state.value.address)

        viewModel.signIn()
        accounts.completion.complete(NextcloudResult.Success(testCredentials))

        assertEquals(Unit, viewModel.finished.first())
        assertEquals(listOf(testSession), accounts.reauthenticatedSessions)
        assertEquals(emptyList<LoginSession>(), accounts.completedSessions)
    }

    @Test
    fun signingInAgainWithAnotherAccountShowsTheReason() {
        val viewModel = LoginViewModel(accounts, savedState, signInAgain = true, initialAddress = "cloud.example.com")

        viewModel.signIn()
        accounts.completion.complete(NextcloudResult.Failure(NextcloudError.DifferentAccount))

        assertEquals(NextcloudError.DifferentAccount, viewModel.state.value.error)
        assertEquals(LoginStep.ENTER_ADDRESS, viewModel.state.value.step)
    }

    @Test
    fun cancellingTheNewLoginStoresNothing() {
        val viewModel = LoginViewModel(accounts, savedState, signInAgain = true, initialAddress = "cloud.example.com")
        viewModel.signIn()

        viewModel.cancel()

        assertEquals(LoginStep.ENTER_ADDRESS, viewModel.state.value.step)
        assertFalse(savedState.contains("login.token"))
        assertNull(accounts.accountState.value)
    }
}
