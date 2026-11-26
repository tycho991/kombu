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
     * Signed in to [server] as [loginName]; [unsyncedChanges] counts the local changes a logout would lose. Which
     * signed-in screen is shown is the back stack's job (design D1 of recipe-browse-edit).
     */
    data class SignedIn(val server: String, val loginName: String, val unsyncedChanges: Int = 0) : MainUiState
}

/** Follows the account; [recipeStatus] gives the changes a logout would lose. */
class MainViewModel(private val accounts: AccountService, recipeStatus: Flow<SyncStatus>) : ViewModel() {
    private val offlineLogoutNotice = MutableStateFlow(false)

    /** Whether the login or the signed-in screens are shown. */
    val state: StateFlow<MainUiState> =
        combine(accounts.account, offlineLogoutNotice, recipeStatus) { account, notice, status ->
            if (account == null) {
                MainUiState.SignedOut(notice)
            } else {
                MainUiState.SignedIn(
                    server = account.server.toString(),
                    loginName = account.loginName,
                    unsyncedChanges = status.pendingCount + status.conflictCount,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    /** Signs out after the user confirmed it. */
    fun logout() {
        viewModelScope.launch {
            val result = accounts.logout()
            offlineLogoutNotice.value = result == LogoutResult.LOCAL_ONLY
        }
    }

    /** Hides the notice shown after an offline logout. */
    fun dismissOfflineLogoutNotice() {
        offlineLogoutNotice.value = false
    }
}
