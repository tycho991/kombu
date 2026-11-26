package io.github.tycho991.kombu.ui.recipes.detail

import androidx.lifecycle.SavedStateHandle
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.ui.TestApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/** Task 5.2: scaling is a view that never changes the stored recipe. */
// Dispatchers.setMain and UnconfinedTestDispatcher are the standard way to test viewModelScope.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RecipeDetailViewModelTest {
    private lateinit var app: TestApp

    private val complete = """{"name":"Pfannkuchen","recipeYield":"4 Portionen",
        "recipeIngredient":["Für den Teig:","200 g Mehl","Saft von 3 Orangen"]}"""

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = TestApp()
    }

    @After
    fun tearDown() {
        app.close()
        Dispatchers.resetMain()
    }

    private suspend fun synced(text: String): Long = app.store.insert(
        LocalRecipe(
            remoteId = "1",
            base = RemoteRecipe(Json.parseToJsonElement(text) as JsonObject),
            state = RecipeState.SYNCED,
        ),
    ).localId

    private fun viewModel(localId: Long, saved: SavedStateHandle = SavedStateHandle()) =
        RecipeDetailViewModel(localId, app.recipes, saved, Locale.ENGLISH)

    @Test
    fun scalingNeitherChangesTheRecipeNorSurvivesANewView() = runTest {
        val localId = synced(complete)
        val before = app.recipes.recipe(localId).first()
        val scaled = viewModel(localId)
        scaled.setServings(6)
        scaled.setServings(0)

        assertEquals(before, app.recipes.recipe(localId).first())
        assertEquals(0L, app.store.syncState().localChangeSeq)
        val fresh = viewModel(localId)
        val state = fresh.state.first { it is RecipeDetailUiState.Shown } as RecipeDetailUiState.Shown
        assertEquals(4, state.servings)
        assertEquals("200 g Mehl", state.ingredients[1].text)
        assertFalse(state.ingredients.any { it.unscaled })
    }

    @Test
    fun theChosenServingsSurviveTheSavedState() = runTest {
        val localId = synced(complete)
        val saved = SavedStateHandle(mapOf("servings" to 8))

        val state = viewModel(
            localId,
            saved = saved,
        ).state.first { it is RecipeDetailUiState.Shown } as RecipeDetailUiState.Shown

        assertEquals(8, state.servings)
        assertEquals("400 g Mehl", state.ingredients[1].text)
        assertEquals(true, state.ingredients[1].let { !it.unscaled && !it.heading })
    }

    @Test
    fun aRecipeWithoutServingsHasNoServingsControl() = runTest {
        val localId = synced("""{"name":"Tee","recipeIngredient":["1 Beutel Tee"]}""")

        val state = viewModel(localId).state.first { it is RecipeDetailUiState.Shown } as RecipeDetailUiState.Shown

        assertNull(state.servings)
        assertEquals("1 Beutel Tee", state.ingredients.single().text)
    }

    @Test
    fun scaledQuantitiesFollowTheKitchenFormOfTheirUnit() = runTest {
        val localId =
            synced(
                """{"name":"Zwiebelsuppe","recipeYield":2,
            "recipeIngredient":["1 Zwiebel","1/2 TL Salz","0,5 l Milch","200 g Mehl"]}""",
            )
        val view = RecipeDetailViewModel(localId, app.recipes, SavedStateHandle(mapOf("servings" to 3)), Locale.GERMAN)

        val state = view.state.first { it is RecipeDetailUiState.Shown } as RecipeDetailUiState.Shown

        assertEquals(listOf("1½ Zwiebel", "¾ TL Salz", "0,75 l Milch", "300 g Mehl"), state.ingredients.map { it.text })
    }

    @Test
    fun theStoredServingsShowQuantitiesInTheKitchenFormToo() = runTest {
        val localId =
            synced(
                """{"name":"Club Sandwich","recipeYield":2,
            "recipeIngredient":["1/2 Limette","1 1/2 TL Senf","100 ml Öl","Salz, Pfeffer"]}""",
            )
        val view = RecipeDetailViewModel(localId, app.recipes, SavedStateHandle(), Locale.GERMAN)

        val state = view.state.first { it is RecipeDetailUiState.Shown } as RecipeDetailUiState.Shown

        assertEquals(listOf("½ Limette", "1½ TL Senf", "100 ml Öl", "Salz, Pfeffer"), state.ingredients.map { it.text })
        assertFalse(state.ingredients.any { it.unscaled })
    }
}
