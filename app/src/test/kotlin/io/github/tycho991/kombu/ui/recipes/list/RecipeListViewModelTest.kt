package io.github.tycho991.kombu.ui.recipes.list

import androidx.lifecycle.SavedStateHandle
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.SortOrder
import io.github.tycho991.kombu.data.recipes.SyncStatus
import io.github.tycho991.kombu.ui.TestApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/** Task 4.2: the sort order is kept for a new list, and search and filters survive in the saved state. */
// Dispatchers.setMain and UnconfinedTestDispatcher are the standard way to test viewModelScope.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RecipeListViewModelTest {
    private lateinit var app: TestApp

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = TestApp(status = MutableStateFlow(SyncStatus(lastSuccessAt = 1_000L)))
        listOf(
            "Chana Masala" to "2024-01-01",
            "Dal" to "2024-02-01",
            "Linsensuppe" to "2025-06-01",
        ).forEach { (name, date) ->
            app.insert("""{"name":"$name","recipeCategory":"Curry","keywords":"vegan","dateModified":"$date 10:00"}""")
        }
    }

    @After
    fun tearDown() {
        app.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = RecipeListViewModel(
        app.recipes,
        app.browse,
        app.dependencies.status,
        MutableStateFlow(false),
        saved,
        Locale.ENGLISH,
    )

    @Test
    fun theSortOrderIsKeptForANewList() = runTest {
        viewModel().setSort(SortOrder.LAST_CHANGE)
        app.browse.sortOrder.first { it == SortOrder.LAST_CHANGE }

        val state = viewModel().state.first { !it.loading && it.listState.sort == SortOrder.LAST_CHANGE }

        assertEquals(listOf("Linsensuppe", "Dal", "Chana Masala"), state.items.map { it.name })
    }

    @Test
    fun searchAndFiltersSurviveANewViewModelFromTheSavedState() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            setSearch("dal")
            toggleKeyword("vegan")
            toggleKeyword("quick")
            toggleKeyword("quick")
            setCategory("Curry")
        }

        val restored = viewModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        val state = restored.state.first { !it.loading }

        assertEquals("dal", state.listState.search)
        assertEquals("Curry", state.listState.category)
        assertEquals(setOf("vegan"), state.listState.keywords)
        assertEquals(listOf("Dal"), state.items.map { it.name })
    }

    @Test
    fun theStatusBarLinkShowsOnlyTheAttentionFilter() = runTest {
        val list = viewModel().apply {
            setSearch("dal")
            showAttention()
        }

        val state = list.state.first { !it.loading && it.listState.attentionOnly }

        assertEquals("", state.listState.search)
        assertEquals(emptyList<String>(), state.items.map { it.name })
        list.clearSearchAndFilters()
        assertEquals(3, list.state.first { !it.listState.attentionOnly }.items.size)
    }

    @Test
    fun aRecipeWithoutNameIsListedWithAnEmptyName() = runTest {
        app.store.insert(
            LocalRecipe(local = RemoteRecipe(Json.parseToJsonElement("{}") as JsonObject), state = RecipeState.CREATED),
        )

        val state = viewModel().state.first { it.items.size == 4 }

        assertEquals(RecipeMarker.PENDING, state.items.first().marker)
        assertEquals("", state.items.first().name)
    }
}
