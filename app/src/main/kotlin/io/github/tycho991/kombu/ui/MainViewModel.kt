package io.github.tycho991.kombu.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tycho991.kombu.data.account.AccountService
import io.github.tycho991.kombu.data.account.LogoutResult
import io.github.tycho991.kombu.data.recipes.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which top-level screen the app shows (spec nextcloud-account, "Entry point depends on login state"). */
sealed interface MainUiState {
    /** The stored login is being read. */
    data object Loading : MainUiState

    /** Nobody is signed in; [offlineLogoutNotice] is true right after a logout that could not reach the server. */
    data class SignedOut(val offlineLogoutNotice: Boolean) : MainUiState

    /**
     * Signed in to [server] as [loginName]; [showAccount] opens the account screen, [signingInAgain] the login for
     * new credentials. [unsyncedChanges] counts the local changes a logout would lose.
     */
    data class SignedIn(
        val server: String,
        val loginName: String,
        val showAccount: Boolean,
        val signingInAgain: Boolean = false,
        val unsyncedChanges: Int = 0,
    ) : MainUiState
}

/** Follows the account and the top-level navigation; [recipeStatus] gives the changes a logout would lose. */
class MainViewModel(private val accounts: AccountService, recipeStatus: Flow<SyncStatus>) : ViewModel() {
    private val screen = MutableStateFlow(Screen.START)
    private val offlineLogoutNotice = MutableStateFlow(false)

    private enum class Screen { START, ACCOUNT, SIGN_IN_AGAIN }

    /** The screen to show. */
    val state: StateFlow<MainUiState> =
        combine(accounts.account, screen, offlineLogoutNotice, recipeStatus) { account, shown, notice, status ->
            if (account == null) {
                MainUiState.SignedOut(notice)
            } else {
                MainUiState.SignedIn(
                    server = account.server.toString(),
                    loginName = account.loginName,
                    showAccount = shown == Screen.ACCOUNT,
                    signingInAgain = shown == Screen.SIGN_IN_AGAIN,
                    unsyncedChanges = status.pendingCount + status.conflictCount,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    /** Opens the account screen. */
    fun openAccount() {
        screen.value = Screen.ACCOUNT
    }

    /** Returns from the account screen or the new login to the start screen. */
    fun closeAccount() {
        screen.value = Screen.START
    }

    /** Opens the login for new credentials after the server rejected the stored ones. */
    fun signInAgain() {
        screen.value = Screen.SIGN_IN_AGAIN
    }

    /** Signs out after the user confirmed it. */
    fun logout() {
        viewModelScope.launch {
            val result = accounts.logout()
            screen.value = Screen.START
            offlineLogoutNotice.value = result == LogoutResult.LOCAL_ONLY
        }
    }

    /** Hides the notice shown after an offline logout. */
    fun dismissOfflineLogoutNotice() {
        offlineLogoutNotice.value = false
    }
}
