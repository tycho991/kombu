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
import io.github.tycho991.kombu.data.recipes.SyncStatus
import io.github.tycho991.kombu.sync.SyncControl
import io.github.tycho991.kombu.ui.account.AccountScreen
import io.github.tycho991.kombu.ui.login.LoginRoute
import io.github.tycho991.kombu.ui.login.LoginViewModel
import io.github.tycho991.kombu.ui.start.StartActions
import io.github.tycho991.kombu.ui.start.StartScreen
import io.github.tycho991.kombu.ui.start.StartViewModel
import kotlinx.coroutines.flow.Flow

/**
 * The top level of the UI: loading, login, start screen, account screen or the new login after rejected
 * credentials, following the account state. [recipeStatus] and [sync] feed the sync status on the start screen.
 */
@Composable
fun KombuApp(accounts: AccountService, recipeStatus: Flow<SyncStatus>, sync: SyncControl) {
    val factory =
        viewModelFactory {
            initializer { MainViewModel(accounts, recipeStatus) }
            initializer { LoginViewModel(accounts, createSavedStateHandle()) }
            initializer { StartViewModel(recipeStatus, sync) }
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

        is MainUiState.SignedIn -> SignedIn(current, main, accounts, factory)
    }
}

@Composable
private fun SignedIn(
    current: MainUiState.SignedIn,
    main: MainViewModel,
    accounts: AccountService,
    factory: androidx.lifecycle.ViewModelProvider.Factory,
) {
    when {
        current.showAccount -> {
            BackHandler(onBack = main::closeAccount)
            AccountScreen(
                server = current.server,
                loginName = current.loginName,
                onBack = main::closeAccount,
                onLogout = main::logout,
                unsyncedChanges = current.unsyncedChanges,
            )
        }

        current.signingInAgain -> {
            BackHandler(onBack = main::closeAccount)
            val signInAgain =
                viewModelFactory {
                    initializer { LoginViewModel(accounts, createSavedStateHandle(), true, current.server) }
                }
            LoginRoute(
                viewModel = viewModel<LoginViewModel>(key = SIGN_IN_AGAIN_KEY, factory = signInAgain),
                offlineLogoutNotice = false,
                onDismissNotice = {},
                onFinished = main::closeAccount,
            )
        }

        else -> {
            val start = viewModel<StartViewModel>(factory = factory)
            val startState by start.state.collectAsStateWithLifecycle()
            StartScreen(
                state = startState,
                actions =
                StartActions(
                    onOpenAccount = main::openAccount,
                    onSyncNow = start::syncNow,
                    onSignInAgain = main::signInAgain,
                ),
            )
        }
    }
}

private const val SIGN_IN_AGAIN_KEY = "sign-in-again"
