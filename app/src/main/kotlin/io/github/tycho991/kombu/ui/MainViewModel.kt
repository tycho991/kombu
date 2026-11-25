package io.github.tycho991.kombu.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tycho991.kombu.data.account.AccountService
import io.github.tycho991.kombu.data.account.LogoutResult
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

    /** Signed in to [server] as [loginName]; [showAccount] opens the account screen. */
    data class SignedIn(val server: String, val loginName: String, val showAccount: Boolean) : MainUiState
}

/** Follows the account and the top-level navigation. */
class MainViewModel(private val accounts: AccountService) : ViewModel() {
    private val showAccount = MutableStateFlow(false)
    private val offlineLogoutNotice = MutableStateFlow(false)

    /** The screen to show. */
    val state: StateFlow<MainUiState> =
        combine(accounts.account, showAccount, offlineLogoutNotice) { account, show, notice ->
            if (account == null) {
                MainUiState.SignedOut(notice)
            } else {
                MainUiState.SignedIn(account.server.toString(), account.loginName, show)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    /** Opens the account screen. */
    fun openAccount() {
        showAccount.value = true
    }

    /** Returns from the account screen to the start screen. */
    fun closeAccount() {
        showAccount.value = false
    }

    /** Signs out after the user confirmed it. */
    fun logout() {
        viewModelScope.launch {
            val result = accounts.logout()
            showAccount.value = false
            offlineLogoutNotice.value = result == LogoutResult.LOCAL_ONLY
        }
    }

    /** Hides the notice shown after an offline logout. */
    fun dismissOfflineLogoutNotice() {
        offlineLogoutNotice.value = false
    }
}
