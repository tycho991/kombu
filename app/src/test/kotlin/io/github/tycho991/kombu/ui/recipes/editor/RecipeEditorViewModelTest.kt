package io.github.tycho991.kombu.ui.recipes.editor

import androidx.lifecycle.SavedStateHandle
import io.github.tycho991.kombu.cookbookclient.RecipeField
import io.github.tycho991.kombu.cookbookclient.RecipeHash
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.UploadError
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
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/** Tasks 6.2-6.4: saving only edited fields, validation, unsaved changes, and syncs during editing. */
// Dispatchers.setMain and UnconfinedTestDispatcher are the standard way to test viewModelScope.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RecipeEditorViewModelTest {
    private lateinit var app: TestApp

    /** Fields Kombu does not show or write itself: a source URL, nutrition and HowToStep steps. */
    private val soup = json(
        """
        {"name":"Soup","description":"Warm","recipeCategory":"Soups","keywords":"warm, quick","recipeYield":"4 Portionen",
         "cookTime":"PT1H5M30S","totalTime":"about an hour","recipeIngredient":["200 g lentils","1 onion"],
         "recipeInstructions":[{"@type":"HowToStep","text":"Chop."},{"@type":"HowToStep","text":"Cook."},
           {"@type":"HowToStep","text":"Serve."}],
         "url":"https://recipes.example.org/soup","nutrition":{"calories":"300 kcal"},"image":"soup.jpg"}
        """,
    )

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

    private fun json(text: String) = RemoteRecipe(Json.parseToJsonElement(text) as JsonObject)

    private suspend fun insert(recipe: LocalRecipe): LocalRecipe = app.store.insert(recipe)

    private suspend fun synced(recipe: RemoteRecipe = soup, remoteId: String = "1") =
        insert(LocalRecipe(remoteId = remoteId, base = recipe, state = RecipeState.SYNCED))

    private fun editor(localId: Long?, saved: SavedStateHandle = SavedStateHandle()) =
        RecipeEditorViewModel(localId, app.recipes, saved, Locale.ENGLISH)

    private suspend fun RecipeEditorViewModel.loaded(): EditorUiState = state.first { !it.loading }

    /** Changes the form and waits until the state shows it, so that later reads are never stale. */
    private suspend fun RecipeEditorViewModel.edit(change: (EditorForm) -> EditorForm): EditorUiState {
        val expected = change(state.value.form)
        update(change)
        return state.first { it.form == expected && it.errors.isEmpty() }
    }

    private suspend fun RecipeEditorViewModel.saveAndWait(): EditorUiState {
        save()
        return state.first { it.finished || it.dialog != null || it.errors.isNotEmpty() || it.saveFailed }
    }

    private suspend fun stored(localId: Long): LocalRecipe = checkNotNull(app.recipes.recipe(localId).first())

    private suspend fun seq(): Long = app.store.syncState().localChangeSeq

    // 6.2 Saving

    @Test
    fun aNameChangeLeavesEveryOtherFieldAsStored() = runTest {
        val row = synced()
        val editor = editor(row.localId)
        assertEquals("Soup", editor.loaded().form.name)

        assertTrue(editor.edit { it.copy(name = "Lentil soup") }.hasChanges)
        assertTrue(editor.saveAndWait().finished)

        val saved = checkNotNull(stored(row.localId).local)
        assertEquals(JsonPrimitive("Lentil soup"), saved.json["name"])
        assertEquals(
            RecipeHash.canonical(JsonObject(soup.json - "name")),
            RecipeHash.canonical(JsonObject(saved.json - "name")),
        )
        assertEquals(1L, seq())
    }

    @Test
    fun savingWithoutChangesWritesNothing() = runTest {
        val row = synced()
        val editor = editor(row.localId)
        editor.loaded()
        // Touching a field and restoring it is no change either.
        editor.edit { it.copy(name = "Other") }
        assertFalse(editor.edit { it.copy(name = "Soup") }.hasChanges)

        assertTrue(editor.saveAndWait().finished)

        assertEquals(row, stored(row.localId))
        assertEquals(0L, seq())
    }

    @Test
    fun reportsEachValidationErrorAndSavesNothing() = runTest {
        synced(json("""{"name":"Linsensuppe"}"""), remoteId = "2")
        val row = synced(json("""{"name":"Bread"}"""))
        val editor = editor(row.localId)
        editor.loaded()

        editor.edit { it.copy(name = " ") }
        assertEquals(setOf(FormError.NAME_REQUIRED), editor.saveAndWait().errors)
        editor.edit { it.copy(name = " linsenSUPPE ") }
        assertEquals(setOf(FormError.NAME_TAKEN), editor.saveAndWait().errors)
        editor.edit { it.copy(name = "Rye", servings = "0", cookTime = TimeInput(minutes = "75")) }
        assertEquals(setOf(FormError.SERVINGS_INVALID, FormError.COOK_TIME_INVALID), editor.saveAndWait().errors)

        assertEquals(emptySet<FormError>(), editor.edit { it.copy(servings = "") }.errors)
        assertEquals(0L, seq())
    }

    @Test
    fun keywordsAndLinesAreNormalisedAndAMovedStepIsSaved() = runTest {
        val row = synced()
        val editor = editor(row.localId)
        editor.loaded()

        editor.edit { it.typingKeyword("Warm, spicy, ").copy(keywordInput = "SPICY") }
        editor.edit { it.copy(ingredients = "200 g lentils\n\n1 onion\n  \n", steps = it.steps.moved(2, 0)) }
        editor.saveAndWait()

        val saved = checkNotNull(stored(row.localId).local).json
        assertEquals(JsonPrimitive("warm,quick,spicy"), saved["keywords"])
        assertEquals("""["Serve.","Chop.","Cook."]""", saved["recipeInstructions"].toString())
        // The ingredient lines are unchanged after dropping empty lines, so they keep their stored form.
        assertEquals(soup.json["recipeIngredient"], saved["recipeIngredient"])
    }

    @Test
    fun anUnreadableTimeIsKeptAndAClearedTimeIsRemoved() = runTest {
        val row = synced()
        val editor = editor(row.localId)
        val opened = editor.loaded().form
        assertEquals(TimeInput(raw = "about an hour"), opened.totalTime)
        assertEquals(TimeInput("1", "5"), opened.cookTime)

        editor.edit { it.copy(cookTime = TimeInput()) }
        editor.saveAndWait()

        val saved = checkNotNull(stored(row.localId).local).json
        assertFalse("cookTime" in saved)
        assertEquals(JsonPrimitive("about an hour"), saved["totalTime"])
    }

    @Test
    fun aNewRecipeIsCreatedAsPending() = runTest {
        val editor = editor(null)
        val state = editor.loaded()
        assertTrue(state.isNew)
        assertFalse(state.hasChanges)

        assertTrue(
            editor.edit { it.copy(name = "Linsensuppe", servings = "2", ingredients = "200 g Linsen") }.hasChanges,
        )
        assertTrue(editor.saveAndWait().finished)

        val created = app.recipes.recipes().first().single()
        assertEquals(RecipeState.CREATED, created.state)
        assertEquals(
            """{"name":"Linsensuppe","recipeYield":2,"recipeIngredient":["200 g Linsen"]}""",
            created.local?.encode(),
        )
        assertEquals(1L, seq())
    }

    @Test
    fun aNewRecipeLeftEmptyIsNotCreated() = runTest {
        val editor = editor(null)
        editor.loaded()

        editor.discard()

        assertTrue(editor.state.first { it.finished }.finished)
        assertEquals(emptyList<LocalRecipe>(), app.recipes.recipes().first())
    }

    @Test
    fun fixingARefusedUploadClearsTheReasonAndSchedulesASync() = runTest {
        val row = insert(
            LocalRecipe(
                local = json("""{"name":"Soup"}"""),
                state = RecipeState.CREATED,
                uploadError = UploadError.NAME_TAKEN,
            ),
        )
        val editor = editor(row.localId)
        editor.loaded()

        editor.edit { it.copy(name = "Lentil soup") }
        editor.saveAndWait()

        val fixed = stored(row.localId)
        assertNull(fixed.uploadError)
        assertEquals("Lentil soup", fixed.shown?.name)
        assertEquals(1L, seq())
    }

    // 6.3 Unsaved changes

    @Test
    fun theFormSurvivesANewViewModelFromTheSavedState() = runTest {
        val row = synced()
        val saved = SavedStateHandle()
        val first = editor(row.localId, saved)
        first.loaded()
        first.edit { it.copy(description = "Typed before the app was stopped") }

        // A sync changes the recipe while the app is stopped; the restored editor keeps what the user typed.
        app.store.replace(stored(row.localId), stored(row.localId).copy(base = soup.with("name", JsonPrimitive("Web"))))
        val restored = editor(row.localId, SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        val state = restored.loaded()

        assertEquals("Typed before the app was stopped", state.form.description)
        assertEquals("Soup", state.form.name)
        assertTrue(state.hasChanges)
    }

    // 6.4 Syncs during editing

    @Test
    fun aSyncChangeInAnotherFieldIsMerged() = runTest {
        val row = synced()
        val editor = editor(row.localId)
        editor.loaded()
        app.store.replace(row, row.copy(base = soup.with("description", JsonPrimitive("From the web"))))

        editor.edit { it.copy(steps = listOf("Everything at once.")) }
        assertTrue(editor.saveAndWait().finished)

        val saved = checkNotNull(stored(row.localId).shown).json
        assertEquals(JsonPrimitive("From the web"), saved["description"])
        assertEquals("""["Everything at once."]""", saved["recipeInstructions"].toString())
    }

    @Test
    fun aSyncChangeInTheSameFieldAsksAndKeepMineSavesTheUsersValue() = runTest {
        val row = synced()
        val editor = editor(row.localId)
        editor.loaded()
        app.store.replace(row, row.copy(base = soup.with("name", JsonPrimitive("Web soup"))))

        editor.edit { it.copy(name = "My soup") }
        val asked = editor.saveAndWait()

        assertEquals(setOf(RecipeField.NAME), (asked.dialog as EditorDialog.Collision).fields)
        assertEquals("Web soup", stored(row.localId).shown?.name)
        editor.keepMine()
        editor.state.first { it.finished }
        assertEquals("My soup", stored(row.localId).shown?.name)
    }

    @Test
    fun takingTheirsPutsTheSyncValueIntoTheFormForAReview() = runTest {
        val row = synced()
        val editor = editor(row.localId)
        editor.loaded()
        app.store.replace(row, row.copy(base = soup.with("name", JsonPrimitive("Web soup"))))
        editor.edit { it.copy(name = "My soup", description = "Mine too") }
        editor.saveAndWait()

        editor.takeTheirs()

        val state = editor.state.first { it.dialog == null && it.form.name == "Web soup" }
        assertNull(state.dialog)
        assertFalse(state.finished)
        assertEquals("Web soup", state.form.name)
        assertEquals("Mine too", state.form.description)
        assertTrue(editor.saveAndWait().finished)
        assertEquals(
            "Mine too",
            stored(row.localId).shown?.json?.get("description")?.let { (it as JsonPrimitive).content },
        )
    }

    @Test
    fun aRecipeDeletedMeanwhileCanBeSavedAsANewRecipe() = runTest {
        val row = synced()
        val editor = editor(row.localId)
        editor.loaded()
        app.store.remove(row)

        editor.edit { it.copy(description = "Still mine") }
        assertEquals(EditorDialog.Gone, editor.saveAndWait().dialog)
        editor.saveAsNew()
        editor.state.first { it.finished }

        val created = app.recipes.recipes().first().single()
        assertEquals(RecipeState.CREATED, created.state)
        assertEquals(JsonPrimitive("Still mine"), created.local?.json?.get("description"))
        assertEquals(soup.json["nutrition"], created.local?.json?.get("nutrition"))
    }

    @Test
    fun aRecipeGoneBeforeOpeningClosesTheEditor() = runTest {
        assertTrue(editor(12345).state.first { it.finished }.finished)
    }
}
