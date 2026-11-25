package io.github.tycho991.kombu.ui

import io.github.tycho991.kombu.data.account.LogoutResult
import io.github.tycho991.kombu.data.recipes.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// Dispatchers.setMain and UnconfinedTestDispatcher are the standard way to test viewModelScope.
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val status = MutableStateFlow(SyncStatus())

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun showsLoginWhenSignedOut() {
        val viewModel = MainViewModel(FakeAccountService(), status)

        assertEquals(MainUiState.SignedOut(offlineLogoutNotice = false), viewModel.state.value)
    }

    @Test
    fun showsStartScreenWhenSignedInAndOpensTheAccount() {
        val viewModel = MainViewModel(FakeAccountService(signedIn = testCredentials), status)

        assertEquals(MainUiState.SignedIn("https://cloud.example.com", "alex", false), viewModel.state.value)
        viewModel.openAccount()
        assertEquals(MainUiState.SignedIn("https://cloud.example.com", "alex", true), viewModel.state.value)
        viewModel.closeAccount()
        assertEquals(MainUiState.SignedIn("https://cloud.example.com", "alex", false), viewModel.state.value)
    }

    @Test
    fun countsTheChangesALogoutWouldLose() {
        val viewModel = MainViewModel(FakeAccountService(signedIn = testCredentials), status)

        status.value = SyncStatus(pendingCount = 2, conflictCount = 1)

        assertEquals(3, (viewModel.state.value as MainUiState.SignedIn).unsyncedChanges)
    }

    @Test
    fun opensAndClosesTheNewLogin() {
        val viewModel = MainViewModel(FakeAccountService(signedIn = testCredentials), status)

        viewModel.signInAgain()
        assertEquals(true, (viewModel.state.value as MainUiState.SignedIn).signingInAgain)
        viewModel.closeAccount()
        assertEquals(MainUiState.SignedIn("https://cloud.example.com", "alex", false), viewModel.state.value)
    }

    @Test
    fun logoutOnlineShowsNoNotice() {
        val viewModel = MainViewModel(FakeAccountService(signedIn = testCredentials), status)
        viewModel.openAccount()

        viewModel.logout()

        assertEquals(MainUiState.SignedOut(offlineLogoutNotice = false), viewModel.state.value)
    }

    @Test
    fun logoutOfflineShowsTheNoticeUntilDismissed() {
        val accounts = FakeAccountService(signedIn = testCredentials).apply { logoutResult = LogoutResult.LOCAL_ONLY }
        val viewModel = MainViewModel(accounts, status)

        viewModel.logout()
        assertEquals(MainUiState.SignedOut(offlineLogoutNotice = true), viewModel.state.value)

        viewModel.dismissOfflineLogoutNotice()
        assertEquals(MainUiState.SignedOut(offlineLogoutNotice = false), viewModel.state.value)
    }
}
