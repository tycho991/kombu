package io.github.tycho991.kombu.ui

import androidx.lifecycle.SavedStateHandle
import io.github.tycho991.kombu.cookbookclient.CookbookClient
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.Resolution
import io.github.tycho991.kombu.data.recipes.UploadError
import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import io.github.tycho991.kombu.sync.SyncEngine
import io.github.tycho991.kombu.sync.SyncResult
import io.github.tycho991.kombu.testcookbookserver.FakeCookbookServer
import io.github.tycho991.kombu.ui.recipes.conflict.ConflictUiState
import io.github.tycho991.kombu.ui.recipes.conflict.ConflictViewModel
import io.github.tycho991.kombu.ui.recipes.detail.RecipeDetailUiState
import io.github.tycho991.kombu.ui.recipes.detail.RecipeDetailViewModel
import io.github.tycho991.kombu.ui.recipes.editor.EditorDialog
import io.github.tycho991.kombu.ui.recipes.editor.EditorForm
import io.github.tycho991.kombu.ui.recipes.editor.EditorUiState
import io.github.tycho991.kombu.ui.recipes.editor.RecipeEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Task 8.1, acceptance criterion A3 end to end on the JVM (design, Test strategy): the real Room database,
 * repository, sync engine and Cookbook client against a Cookbook in memory, driven by the editor, recipe view and
 * conflict view models. Offline phases are modelled by not syncing; a sync is one explicit [sync] call.
 */
// Dispatchers.setMain and UnconfinedTestDispatcher are the standard way to test viewModelScope.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConflictEndToEndTest {
    private val server = FakeCookbookServer()
    private val web = MockWebServer()
    private lateinit var app: TestApp
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        web.dispatcher = server
        web.start()
        app = TestApp()
        val credentials = NextcloudCredentials(ServerAddress.of(web.url("/nextcloud")), "alex", "app-password")
        engine = SyncEngine(CookbookClient(OkHttpClient()), app.store, { credentials })
    }

    @After
    fun tearDown() {
        app.close()
        web.close()
        Dispatchers.resetMain()
    }

    private suspend fun sync() = assertEquals(SyncResult.Success, engine.run())

    private suspend fun local(name: String): LocalRecipe = app.recipes.recipes().first().single {
        it.shown?.name == name
    }

    private fun JsonObject.text(key: String): String? = get(key)?.jsonPrimitive?.content

    private fun serverRecipe(id: String): JsonObject = server.stored.getValue(id)

    private fun editor(localId: Long?) = RecipeEditorViewModel(localId, app.recipes, SavedStateHandle(), Locale.ENGLISH)

    /** Opens the editor, applies [change] and waits until the state shows it. */
    private suspend fun RecipeEditorViewModel.open(change: (EditorForm) -> EditorForm): RecipeEditorViewModel {
        val opened = state.first { !it.loading }.form
        update(change)
        state.first { it.form == change(opened) }
        return this
    }

    private suspend fun RecipeEditorViewModel.saveAndWait(): EditorUiState {
        save()
        return state.first { it.finished || it.dialog != null || it.errors.isNotEmpty() }
    }

    /** A soup on the server, synced to the device. */
    private suspend fun syncedSoup(): String {
        val id = server.add(FakeCookbookServer.recipe("Soup"))
        sync()
        return id
    }

    /** Edits the description offline, changes the name and description on the web, and syncs into a conflict. */
    private suspend fun conflictOverSoup(): Pair<String, Long> {
        val id = syncedSoup()
        val soup = local("Soup")
        assertTrue(editor(soup.localId).open { it.copy(description = "Mine") }.saveAndWait().finished)
        server.edit(
            id,
        ) { JsonObject(it + ("name" to JsonPrimitive("Web soup")) + ("description" to JsonPrimitive("Theirs"))) }

        sync()

        val row = checkNotNull(app.recipes.recipe(soup.localId).first())
        assertEquals(RecipeState.CONFLICT, row.state)
        assertEquals("Mine", row.local?.json?.text("description"))
        assertEquals("Theirs", row.server?.json?.text("description"))
        assertEquals("Theirs", serverRecipe(id).text("description"))
        return id to soup.localId
    }

    private suspend fun resolve(localId: Long, resolution: Resolution) {
        val view = ConflictViewModel(localId, app.recipes)
        val shown = view.state.first { it is ConflictUiState.Shown } as ConflictUiState.Shown
        assertEquals(listOf("NAME", "DESCRIPTION"), shown.fields.map { it.name })
        view.choose(resolution)
        view.state.first { (it as? ConflictUiState.Shown)?.pending != null }
        view.confirm()
        view.state.first { it is ConflictUiState.Resolved }
    }

    @Test
    fun anOfflineEditIsUploaded() = runTest {
        val id = syncedSoup()
        val soup = local("Soup")

        editor(soup.localId).open { it.copy(description = "Warm and filling") }.saveAndWait()
        assertEquals(RecipeState.CHANGED, local("Soup").state)
        sync()

        assertEquals("Warm and filling", serverRecipe(id).text("description"))
        assertEquals(RecipeState.SYNCED, local("Soup").state)
        assertEquals(FakeCookbookServer.recipe("Soup")["nutrition"], serverRecipe(id)["nutrition"])
    }

    @Test
    fun anOfflineEditMeetsAWebEditAndKeepMineWinsOnTheServer() = runTest {
        val (id, localId) = conflictOverSoup()

        resolve(localId, Resolution.KEEP_MINE)
        assertEquals(RecipeState.CHANGED, app.recipes.recipe(localId).first()?.state)
        sync()

        assertEquals("Mine", serverRecipe(id).text("description"))
        assertEquals("Soup", serverRecipe(id).text("name"))
        assertEquals(RecipeState.SYNCED, app.recipes.recipe(localId).first()?.state)
    }

    @Test
    fun anOfflineEditMeetsAWebEditAndNextcloudsVersionIsTaken() = runTest {
        val (id, localId) = conflictOverSoup()
        val before = server.bodies.size

        resolve(localId, Resolution.TAKE_SERVER)
        sync()

        assertEquals(before, server.bodies.size)
        assertEquals("Theirs", serverRecipe(id).text("description"))
        val row = checkNotNull(app.recipes.recipe(localId).first())
        assertEquals(RecipeState.SYNCED, row.state)
        assertEquals("Web soup", row.shown?.name)
    }

    @Test
    fun aSyncDuringEditingKeepsAChangeInAnotherFieldAndAsksForTheSameField() = runTest {
        val id = syncedSoup()
        val soup = local("Soup")

        // Another field: the description from the web and the user's steps both survive.
        val steps = editor(soup.localId).open { it.copy(steps = listOf("Simmer.")) }
        server.edit(id) { JsonObject(it + ("description" to JsonPrimitive("From the web"))) }
        sync()
        assertTrue(steps.saveAndWait().finished)
        sync()
        assertEquals("From the web", serverRecipe(id).text("description"))
        assertEquals("""["Simmer."]""", serverRecipe(id)["recipeInstructions"].toString())

        // The same field: the editor names it, and keeping mine uploads the user's name.
        val name = editor(soup.localId).open { it.copy(name = "My soup") }
        server.edit(id) { JsonObject(it + ("name" to JsonPrimitive("Web soup"))) }
        sync()
        val asked = name.saveAndWait().dialog as EditorDialog.Collision
        assertEquals(listOf("NAME"), asked.fields.map { it.name })
        name.keepMine()
        name.state.first { it.finished }
        sync()
        assertEquals("My soup", serverRecipe(id).text("name"))
        assertEquals("From the web", serverRecipe(id).text("description"))
    }

    @Test
    fun aRecipeIsCreatedRenamedAfterARefusalAndDeleted() = runTest {
        syncedSoup()
        // Someone creates "Bread" on the web after the last sync, so the local name check cannot know it.
        server.add(FakeCookbookServer.recipe("Bread"))

        assertTrue(editor(null).open { it.copy(name = "Bread", ingredients = "500 g flour") }.saveAndWait().finished)
        sync()
        val refused = app.recipes.recipes().first().single { it.state == RecipeState.CREATED }
        assertEquals(UploadError.NAME_TAKEN, refused.uploadError)

        assertTrue(editor(refused.localId).open { it.copy(name = "Rye bread") }.saveAndWait().finished)
        assertNull(app.recipes.recipe(refused.localId).first()?.uploadError)
        sync()
        val rye = server.stored.entries.single { it.value.text("name") == "Rye bread" }
        assertEquals("""["500 g flour"]""", rye.value["recipeIngredient"].toString())
        assertEquals(RecipeState.SYNCED, app.recipes.recipe(refused.localId).first()?.state)

        val detail = RecipeDetailViewModel(refused.localId, app.recipes, SavedStateHandle(), Locale.ENGLISH)
        detail.state.first { it is RecipeDetailUiState.Shown }
        detail.delete()
        detail.state.first { it is RecipeDetailUiState.Gone }
        sync()

        assertNull(server.stored[rye.key])
        assertNull(app.recipes.recipe(refused.localId).first())
    }
}
