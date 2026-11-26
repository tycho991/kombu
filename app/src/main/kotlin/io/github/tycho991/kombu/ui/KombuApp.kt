package io.github.tycho991.kombu.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.tycho991.kombu.ui.account.AccountScreen
import io.github.tycho991.kombu.ui.login.LoginRoute
import io.github.tycho991.kombu.ui.login.LoginViewModel
import io.github.tycho991.kombu.ui.navigation.Route
import io.github.tycho991.kombu.ui.recipes.detail.RecipeDetailRoute
import io.github.tycho991.kombu.ui.recipes.detail.RecipeDetailViewModel
import io.github.tycho991.kombu.ui.recipes.editor.RecipeEditorRoute
import io.github.tycho991.kombu.ui.recipes.editor.RecipeEditorViewModel
import io.github.tycho991.kombu.ui.recipes.list.RecipeListRoute
import io.github.tycho991.kombu.ui.recipes.list.RecipeListViewModel
import io.github.tycho991.kombu.ui.start.StartActions
import io.github.tycho991.kombu.ui.start.StartScreen
import io.github.tycho991.kombu.ui.start.StartViewModel

/**
 * The top level of the UI: loading, login, or the signed-in screens, following the account state. Signing out
 * leaves the signed-in part, so its back stack starts again at the recipe list after the next login.
 */
@Composable
fun KombuApp(dependencies: KombuDependencies) {
    val factory =
        viewModelFactory {
            initializer { MainViewModel(dependencies.accounts, dependencies.status) }
            initializer { LoginViewModel(dependencies.accounts, createSavedStateHandle()) }
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

        is MainUiState.SignedIn -> SignedIn(current, main, dependencies)
    }
}

/** The signed-in screens on a Navigation 3 back stack with the recipe list as its root (design D1). */
@Composable
private fun SignedIn(current: MainUiState.SignedIn, main: MainViewModel, dependencies: KombuDependencies) {
    val backStack = rememberNavBackStack(Route.RecipeList)
    val back: () -> Unit = { backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Route.RecipeList> { RecipeListEntry(dependencies, backStack) }
            entry<Route.RecipeDetail> { key -> RecipeDetailEntry(key, dependencies, backStack) }
            entry<Route.RecipeEditor> { key -> RecipeEditorEntry(key, dependencies, backStack) }
            entry<Route.SyncStatus> { SyncStatusEntry(dependencies, backStack) }
            entry<Route.Account> {
                AccountScreen(
                    server = current.server,
                    loginName = current.loginName,
                    onBack = back,
                    onLogout = main::logout,
                    unsyncedChanges = current.unsyncedChanges,
                )
            }
            entry<Route.SignInAgain> {
                val signInAgain = viewModelFactory {
                    initializer {
                        LoginViewModel(
                            dependencies.accounts,
                            createSavedStateHandle(),
                            true,
                            current.server,
                        )
                    }
                }
                LoginRoute(
                    viewModel = viewModel<LoginViewModel>(factory = signInAgain),
                    offlineLogoutNotice = false,
                    onDismissNotice = {},
                    onFinished = back,
                )
            }
        },
    )
}

@Composable
private fun RecipeListEntry(dependencies: KombuDependencies, backStack: NavBackStack<NavKey>) {
    val locale = LocalConfiguration.current.locales[0]
    val list = viewModel<RecipeListViewModel>(
        factory = viewModelFactory {
            initializer {
                RecipeListViewModel(
                    recipes = dependencies.recipes,
                    preferences = dependencies.browse,
                    status = dependencies.status,
                    running = dependencies.sync.running,
                    saved = createSavedStateHandle(),
                    locale = locale,
                )
            }
        },
    )
    RecipeListRoute(
        viewModel = list,
        onOpenRecipe = { backStack.add(Route.RecipeDetail(it)) },
        onCreate = { backStack.add(Route.RecipeEditor()) },
        onOpenAccount = { backStack.add(Route.Account) },
        onOpenSyncStatus = { backStack.add(Route.SyncStatus) },
    )
}

@Composable
private fun RecipeDetailEntry(
    key: Route.RecipeDetail,
    dependencies: KombuDependencies,
    backStack: NavBackStack<NavKey>,
) {
    val locale = LocalConfiguration.current.locales[0]
    val detail = viewModel<RecipeDetailViewModel>(
        factory = viewModelFactory {
            initializer { RecipeDetailViewModel(key.localId, dependencies.recipes, createSavedStateHandle(), locale) }
        },
    )
    RecipeDetailRoute(
        viewModel = detail,
        onBack = { backStack.removeLastOrNull() },
        // The recipe was deleted or removed by a sync: leave its view (design D10).
        onClose = { backStack.remove(key) },
        onEdit = { backStack.add(Route.RecipeEditor(key.localId)) },
    )
}

@Composable
private fun RecipeEditorEntry(
    key: Route.RecipeEditor,
    dependencies: KombuDependencies,
    backStack: NavBackStack<NavKey>,
) {
    val locale = LocalConfiguration.current.locales[0]
    val editor = viewModel<RecipeEditorViewModel>(
        factory = viewModelFactory {
            initializer { RecipeEditorViewModel(key.localId, dependencies.recipes, createSavedStateHandle(), locale) }
        },
    )
    RecipeEditorRoute(viewModel = editor, onDone = { backStack.remove(key) })
}

@Composable
private fun SyncStatusEntry(dependencies: KombuDependencies, backStack: NavBackStack<NavKey>) {
    val start = viewModel<StartViewModel>(
        factory = viewModelFactory { initializer { StartViewModel(dependencies.status, dependencies.sync) } },
    )
    val startState by start.state.collectAsStateWithLifecycle()
    StartScreen(
        state = startState,
        actions = StartActions(
            onBack = { backStack.removeLastOrNull() },
            onSyncNow = start::syncNow,
            onSignInAgain = { backStack.add(Route.SignInAgain) },
        ),
    )
}
