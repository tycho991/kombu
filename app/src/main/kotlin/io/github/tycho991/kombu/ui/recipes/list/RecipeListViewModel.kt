package io.github.tycho991.kombu.ui.recipes.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tycho991.kombu.cookbookclient.ImageSize
import io.github.tycho991.kombu.cookbookclient.RecipeFields
import io.github.tycho991.kombu.data.recipes.BrowsePreferences
import io.github.tycho991.kombu.data.recipes.ListState
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeQuery
import io.github.tycho991.kombu.data.recipes.RecipeRepository
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.SortOrder
import io.github.tycho991.kombu.data.recipes.SyncProblem
import io.github.tycho991.kombu.data.recipes.SyncStatus
import io.github.tycho991.kombu.data.recipes.needsAttention
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** What a list entry shows besides name and category (spec "Recipe markers in the list"). */
enum class RecipeMarker {
    /** A change is not uploaded yet. */
    PENDING,

    /** The recipe is in conflict. */
    CONFLICT,

    /** The server refused the upload. */
    ATTENTION,
}

/** One entry of the recipe list. [thumbnailVersion] changes when the thumbnail file is replaced (design D2). */
data class RecipeListItem(
    val localId: Long,
    val name: String,
    val category: String?,
    val thumbnail: File? = null,
    val thumbnailVersion: Long = 0,
    val marker: RecipeMarker? = null,
)

/** Why the list is empty (design D8). */
sealed interface EmptyList {
    /** No recipes yet and the first sync is running or about to start. */
    data object Downloading : EmptyList

    /** No recipes yet and the first sync failed with [problem]. */
    data class FirstSyncFailed(val problem: SyncProblem) : EmptyList

    /** A sync completed and the account has no recipes. */
    data object NoRecipes : EmptyList

    /** Recipes exist, but none matches the search and filters. */
    data object NothingFound : EmptyList
}

/** The compact sync status above the list (spec "Sync status bar"). */
data class SyncBarState(
    val status: SyncStatus = SyncStatus(),
    val running: Boolean = false,
    /** Recipes in conflict or with a refused upload. */
    val attentionCount: Int = 0,
)

/** The state of the recipe list screen. */
data class RecipeListUiState(
    val loading: Boolean = true,
    val items: List<RecipeListItem> = emptyList(),
    val listState: ListState = ListState(),
    val categories: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val empty: EmptyList? = null,
    val bar: SyncBarState = SyncBarState(),
)

/**
 * The recipe list: the local copy filtered and sorted by the list state (design D7, D8 of recipe-browse-edit).
 * Search text and filters live in [saved] and survive process death; the sort order is a stored preference.
 *
 * @param locale the app language, for the alphabetical order.
 */
class RecipeListViewModel(
    private val recipes: RecipeRepository,
    private val preferences: BrowsePreferences,
    status: Flow<SyncStatus>,
    running: Flow<Boolean>,
    private val saved: SavedStateHandle,
    locale: Locale,
    computation: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val query = RecipeQuery(locale)

    private val filters: Flow<ListState> =
        combine(
            saved.getStateFlow(SEARCH, ""),
            saved.getStateFlow<String?>(CATEGORY, null),
            saved.getStateFlow(KEYWORDS, arrayListOf<String>()),
            saved.getStateFlow(ATTENTION, false),
            preferences.sortOrder,
        ) { search, category, keywords, attention, sort ->
            ListState(search, category, keywords.toSet(), attention, sort)
        }

    private val indexed = recipes.recipes().map { all -> Indexed(all, query.index(all)) }.flowOn(computation)

    private class Indexed(val all: List<LocalRecipe>, val index: RecipeQuery.Index)

    /** The state of the screen. */
    val state: StateFlow<RecipeListUiState> =
        combine(indexed, filters, status, running) { current, listState, syncStatus, isRunning ->
            val visible = query.query(current.index, listState)
            RecipeListUiState(
                loading = false,
                items = visible.map(::item),
                listState = listState,
                categories = query.categories(current.all),
                keywords = query.keywords(current.all),
                empty = emptyReason(current.all, visible, syncStatus, isRunning),
                bar = SyncBarState(syncStatus, isRunning, current.all.count { it.needsAttention }),
            )
        }.flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RecipeListUiState())

    /** Sets the search text. */
    fun setSearch(text: String) {
        saved[SEARCH] = text
    }

    /** Shows only recipes of [category], or all when it is `null`. */
    fun setCategory(category: String?) {
        saved[CATEGORY] = category
    }

    /** Adds [keyword] to the keyword filter, or removes it when it is set. */
    fun toggleKeyword(keyword: String) {
        val current = saved.get<ArrayList<String>>(KEYWORDS).orEmpty()
        saved[KEYWORDS] = ArrayList(if (keyword in current) current - keyword else current + keyword)
    }

    /** Shows only recipes that need attention, or all recipes. */
    fun setAttentionOnly(only: Boolean) {
        saved[ATTENTION] = only
    }

    /** Clears the search and every filter; the sort order stays. */
    fun clearSearchAndFilters() {
        saved[SEARCH] = ""
        saved[CATEGORY] = null
        saved[KEYWORDS] = arrayListOf<String>()
        saved[ATTENTION] = false
    }

    /** Shows the recipes that need attention, from the sync status bar (spec "Attention filter"). */
    fun showAttention() {
        clearSearchAndFilters()
        saved[ATTENTION] = true
    }

    /** Stores [order] as the sort order. */
    fun setSort(order: SortOrder) {
        viewModelScope.launch { preferences.setSortOrder(order) }
    }

    private fun item(recipe: LocalRecipe): RecipeListItem {
        val values = recipe.shown?.let(RecipeFields::read)
        val thumbnail = recipes.imageFile(recipe.localId, ImageSize.THUMB)
        return RecipeListItem(
            localId = recipe.localId,
            name = values?.name.orEmpty(),
            category = values?.category,
            thumbnail = thumbnail,
            thumbnailVersion = thumbnail?.lastModified() ?: 0,
            marker = when {
                recipe.state == RecipeState.CONFLICT -> RecipeMarker.CONFLICT
                recipe.uploadError != null -> RecipeMarker.ATTENTION
                recipe.isPending -> RecipeMarker.PENDING
                else -> null
            },
        )
    }

    private fun emptyReason(
        all: List<LocalRecipe>,
        visible: List<LocalRecipe>,
        status: SyncStatus,
        running: Boolean,
    ): EmptyList? {
        val problem = status.problem
        return when {
            visible.isNotEmpty() -> null
            all.isNotEmpty() -> EmptyList.NothingFound
            status.lastSuccessAt != null -> EmptyList.NoRecipes
            problem != null && !running -> EmptyList.FirstSyncFailed(problem)
            else -> EmptyList.Downloading
        }
    }

    private companion object {
        const val SEARCH = "search"
        const val CATEGORY = "category"
        const val KEYWORDS = "keywords"
        const val ATTENTION = "attention"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
