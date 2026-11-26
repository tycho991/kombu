package io.github.tycho991.kombu.ui.recipes.conflict

import io.github.tycho991.kombu.cookbookclient.RecipeField
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.Resolution
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Task 7.2: every choice of every conflict kind, mapped as design D6 says, and a conflict changed meanwhile. */
// Dispatchers.setMain and UnconfinedTestDispatcher are the standard way to test viewModelScope.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConflictViewModelTest {
    private lateinit var app: TestApp

    private val base = json("""{"name":"Soup","description":"Old","nutrition":{"calories":"1"}}""")
    private val mine = json("""{"name":"Soup","description":"Mine","nutrition":{"calories":"1"}}""")
    private val theirs = json("""{"name":"Web soup","description":"Theirs","nutrition":{"calories":"2"}}""")

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

    private suspend fun conflict(kind: ConflictKind): LocalRecipe = app.store.insert(
        LocalRecipe(
            remoteId = "1",
            base = base,
            local = mine.takeIf { kind != ConflictKind.CHANGED_ON_SERVER_DELETED_HERE },
            server = theirs.takeIf { kind != ConflictKind.DELETED_ON_SERVER },
            state = RecipeState.CONFLICT,
            conflict = kind,
        ),
    )

    private suspend fun ConflictViewModel.shown() = state.first { it is ConflictUiState.Shown } as ConflictUiState.Shown

    /** Chooses and confirms [resolution], then returns the stored row, or `null` when it was removed. */
    private suspend fun resolve(kind: ConflictKind, resolution: Resolution): LocalRecipe? {
        val row = conflict(kind)
        val view = ConflictViewModel(row.localId, app.recipes)
        view.shown()
        view.choose(resolution)
        val asked = view.state.first { (it as? ConflictUiState.Shown)?.pending != null } as ConflictUiState.Shown
        assertEquals(resolution, asked.pending?.resolution)
        view.confirm()
        assertEquals(ConflictUiState.Resolved, view.state.first { it is ConflictUiState.Resolved })
        assertEquals(1L, app.store.syncState().localChangeSeq)
        return app.recipes.recipe(row.localId).first()
    }

    @Test
    fun showsTheDifferingFieldsOfBothVersions() = runTest {
        val row = conflict(ConflictKind.BOTH_CHANGED)

        val shown = ConflictViewModel(row.localId, app.recipes).shown()

        assertEquals(listOf(RecipeField.NAME, RecipeField.DESCRIPTION), shown.fields)
        assertEquals(1, shown.otherFields)
        assertEquals("Mine", shown.mine?.description)
        assertEquals("Theirs", shown.theirs?.description)
    }

    @Test
    fun bothChangedKeepMineUploadsMyVersion() = runTest {
        val row = checkNotNull(resolve(ConflictKind.BOTH_CHANGED, Resolution.KEEP_MINE))

        assertEquals(RecipeState.CHANGED, row.state)
        assertEquals(mine, row.local)
        assertEquals(theirs, row.base)
    }

    @Test
    fun bothChangedTakeServerShowsTheServerVersion() = runTest {
        val row = checkNotNull(resolve(ConflictKind.BOTH_CHANGED, Resolution.TAKE_SERVER))

        assertEquals(RecipeState.SYNCED, row.state)
        assertEquals(theirs, row.shown)
        assertNull(row.local)
    }

    @Test
    fun deletedOnServerKeepCreatesTheRecipeAgain() = runTest {
        val row = checkNotNull(resolve(ConflictKind.DELETED_ON_SERVER, Resolution.KEEP_MINE))

        assertEquals(RecipeState.CREATED, row.state)
        assertNull(row.remoteId)
        assertEquals(mine, row.local)
    }

    @Test
    fun deletedOnServerDeleteRemovesIt() = runTest {
        assertNull(resolve(ConflictKind.DELETED_ON_SERVER, Resolution.TAKE_SERVER))
    }

    @Test
    fun deletedHereDeleteAnywayKeepsTheDeletion() = runTest {
        val row = checkNotNull(resolve(ConflictKind.CHANGED_ON_SERVER_DELETED_HERE, Resolution.KEEP_MINE))

        assertEquals(RecipeState.DELETED, row.state)
        assertEquals(theirs, row.base)
    }

    @Test
    fun deletedHereKeepServerRestoresTheServerVersion() = runTest {
        val row = checkNotNull(resolve(ConflictKind.CHANGED_ON_SERVER_DELETED_HERE, Resolution.TAKE_SERVER))

        assertEquals(RecipeState.SYNCED, row.state)
        assertEquals(theirs, row.shown)
    }

    @Test
    fun cancellingAChoiceAppliesNothing() = runTest {
        val row = conflict(ConflictKind.BOTH_CHANGED)
        val view = ConflictViewModel(row.localId, app.recipes)
        view.shown()

        view.choose(Resolution.TAKE_SERVER)
        view.cancel()

        assertNull(view.shown().pending)
        assertEquals(row, app.recipes.recipe(row.localId).first())
    }

    @Test
    fun aConflictChangedMeanwhileIsShownAgainInsteadOfApplied() = runTest {
        val row = conflict(ConflictKind.BOTH_CHANGED)
        val view = ConflictViewModel(row.localId, app.recipes)
        view.shown()
        view.choose(Resolution.KEEP_MINE)

        // A sync stores a newer server version while the confirmation is open.
        val newer = json("""{"name":"Newer web soup","description":"Theirs","nutrition":{"calories":"2"}}""")
        assertTrue(app.store.replace(row, row.copy(server = newer)))
        view.confirm()

        val shown = view.state.first { it is ConflictUiState.Shown && it.changedMeanwhile } as ConflictUiState.Shown
        assertEquals("Newer web soup", shown.theirs?.name)
        assertEquals(RecipeState.CONFLICT, app.recipes.recipe(row.localId).first()?.state)
        view.choose(Resolution.TAKE_SERVER)
        val again = view.state.first { it is ConflictUiState.Shown && it.pending != null } as ConflictUiState.Shown
        assertEquals(false, again.changedMeanwhile)
    }

    @Test
    fun aRecipeThatIsNoConflictCountsAsResolved() = runTest {
        val row = app.store.insert(LocalRecipe(remoteId = "9", base = base, state = RecipeState.SYNCED))

        assertEquals(
            ConflictUiState.Resolved,
            ConflictViewModel(row.localId, app.recipes).state.first { it !is ConflictUiState.Loading },
        )
    }
}
