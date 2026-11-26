package io.github.tycho991.kombu.ui

import io.github.tycho991.kombu.data.account.AccountService
import io.github.tycho991.kombu.data.recipes.BrowsePreferences
import io.github.tycho991.kombu.data.recipes.RecipeRepository
import io.github.tycho991.kombu.data.recipes.SyncStatus
import io.github.tycho991.kombu.sync.SyncControl
import kotlinx.coroutines.flow.Flow

/** What the UI needs from the rest of the app; tests pass fakes and an in-memory database. */
class KombuDependencies(
    val accounts: AccountService,
    val recipes: RecipeRepository,
    val browse: BrowsePreferences,
    val sync: SyncControl,
    /** The stored sync status; tests may replace it to show a state directly. */
    val status: Flow<SyncStatus> = recipes.status(),
)
