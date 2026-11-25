package io.github.tycho991.kombu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tycho991.kombu.data.account.AccountService
import io.github.tycho991.kombu.ui.account.AccountScreen
import io.github.tycho991.kombu.ui.login.LoginRoute
import io.github.tycho991.kombu.ui.login.LoginViewModel
import io.github.tycho991.kombu.ui.start.StartScreen

/** The top level of the UI: loading, login, start screen or account screen, following the account state. */
@Composable
fun KombuApp(accounts: AccountService) {
    val factory =
        viewModelFactory {
            initializer { MainViewModel(accounts) }
            initializer { LoginViewModel(accounts, createSavedStateHandle()) }
        }
    val main: MainViewModel = viewModel(factory = factory)
    val state by main.state.collectAsStateWithLifecycle()
    when (val current = state) {
        MainUiState.Loading ->
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

        is MainUiState.SignedOut ->
            LoginRoute(
                viewModel = viewModel<LoginViewModel>(factory = factory),
                offlineLogoutNotice = current.offlineLogoutNotice,
                onDismissNotice = main::dismissOfflineLogoutNotice,
            )

        is MainUiState.SignedIn ->
            if (current.showAccount) {
                BackHandler(onBack = main::closeAccount)
                AccountScreen(
                    server = current.server,
                    loginName = current.loginName,
                    onBack = main::closeAccount,
                    onLogout = main::logout,
                )
            } else {
                StartScreen(onOpenAccount = main::openAccount)
            }
    }
}
