package io.github.tycho991.kombu.data.recipes

import io.github.tycho991.kombu.cookbookclient.RecipeField
import io.github.tycho991.kombu.cookbookclient.RecipeValues
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Locale

/** Task 3.2: the editor's save with a compare step (design D5), the name check and the derived flows. */
@RunWith(RobolectricTestRunner::class)
class RecipeEditTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = inMemoryDatabase()
    private var now = 1_000L
    private val images by lazy { ImageStore(File(folder.root, "images")) }
    private val repository by lazy { RecipeRepository(database, images) { now } }
    private val store by lazy { RoomRecipeStore(database, images) }

    @After
    fun close() {
        database.close()
    }

    private suspend fun stored(localId: Long): LocalRecipe = checkNotNull(repository.recipe(localId).first())

    private suspend fun seq(): Long = store.syncState().localChangeSeq

    private fun RemoteRecipe.described(text: String) = with("description", JsonPrimitive(text))

    private fun rename(name: String) = RecipeChange(setOf(RecipeField.NAME), RecipeValues(name = name))

    /** What a sync does when it downloads a newer server version of a synced recipe. */
    private suspend fun syncDownloads(row: LocalRecipe, change: (RemoteRecipe) -> RemoteRecipe): Boolean =
        store.replace(row, row.copy(base = change(checkNotNull(row.base))))

    @Test
    fun `an unchanged recipe is edited`() = runTest {
        val row = store.insert(synced("Soup", "1"))

        val result = repository.edit(row.localId, checkNotNull(row.shown), rename("Stew"))

        assertEquals(EditResult.Edited, result)
        val edited = stored(row.localId)
        assertEquals(RecipeState.CHANGED, edited.state)
        assertEquals("Stew", edited.shown?.name)
        assertEquals(1_000L, edited.localChangeAt)
        assertEquals(1L, seq())
    }

    @Test
    fun `a sync change in another field is kept`() = runTest {
        val row = store.insert(synced("Soup", "1"))
        val opened = checkNotNull(row.shown)
        assertTrue(syncDownloads(row) { it.described("From the web") })

        val result = repository.edit(row.localId, opened, rename("Stew"))

        assertEquals(EditResult.Edited, result)
        val edited = checkNotNull(stored(row.localId).shown)
        assertEquals("Stew", edited.name)
        assertEquals(JsonPrimitive("From the web"), edited.json["description"])
    }

    @Test
    fun `a sync change in the same field is a collision`() = runTest {
        val row = store.insert(synced("Soup", "1"))
        val opened = checkNotNull(row.shown)
        assertTrue(syncDownloads(row) { it.renamed("Web soup") })
        val before = stored(row.localId)

        val result = repository.edit(row.localId, opened, rename("Stew"))

        val collision = result as EditResult.Collision
        assertEquals(setOf(RecipeField.NAME), collision.fields)
        assertEquals("Web soup", collision.current.name)
        assertEquals(before, stored(row.localId))
        assertEquals(0L, seq())

        // "Save mine anyway" edits again with the current version as the expected one.
        assertEquals(EditResult.Edited, repository.edit(row.localId, collision.current, rename("Stew")))
        assertEquals("Stew", stored(row.localId).shown?.name)
    }

    @Test
    fun `a removed or deleted recipe is gone`() = runTest {
        val removed = store.insert(synced("Soup", "1"))
        val deleted = store.insert(synced("Bread", "2"))
        assertTrue(store.remove(removed))
        assertTrue(repository.delete(deleted.localId))

        assertEquals(EditResult.Gone, repository.edit(removed.localId, checkNotNull(removed.shown), rename("Stew")))
        assertEquals(EditResult.Gone, repository.edit(deleted.localId, checkNotNull(deleted.shown), rename("Rye")))
        assertEquals(EditResult.Gone, repository.edit(999, checkNotNull(removed.shown), rename("Ghost")))
    }

    @Test
    fun `an edit that changes nothing stores nothing`() = runTest {
        val row = store.insert(synced("Soup", "1"))

        assertEquals(EditResult.Edited, repository.edit(row.localId, checkNotNull(row.shown), rename("Soup")))

        assertEquals(row, stored(row.localId))
        assertEquals(0L, seq())
    }

    @Test
    fun `the local change counter grows exactly once per successful edit`() = runTest {
        val row = store.insert(synced("Soup", "1"))

        repository.edit(row.localId, checkNotNull(row.shown), rename("Stew"))
        repository.edit(row.localId, checkNotNull(stored(row.localId).shown), rename("Goulash"))
        repository.edit(row.localId, checkNotNull(row.shown), rename("Chili"))

        assertEquals("Goulash", stored(row.localId).shown?.name)
        assertEquals(2L, seq())
    }

    @Test
    fun `an edit clears a refused upload`() = runTest {
        val localId = repository.create(recipe("Soup"))
        val created = stored(localId)
        assertTrue(store.replace(created, created.copy(uploadError = UploadError.NAME_TAKEN)))
        now = 2_000L

        repository.edit(localId, checkNotNull(created.shown), rename("Lentil soup"))

        val fixed = stored(localId)
        assertNull(fixed.uploadError)
        assertEquals(RecipeState.CREATED, fixed.state)
        assertEquals(2_000L, fixed.localChangeAt)
    }

    @Test
    fun `a sync write racing the edit never loses either change`() = runTest {
        repeat(RACE_ROUNDS) { round ->
            val row = store.insert(synced("Soup $round", "r$round"))
            val opened = checkNotNull(row.shown)

            val (edit, replaced) = withContext(Dispatchers.Default) {
                val edit = async { repository.edit(row.localId, opened, rename("Mine $round")) }
                val sync = async { syncDownloads(row) { it.described("Web $round") } }
                edit.await() to sync.await()
            }

            val shown = checkNotNull(stored(row.localId).shown)
            assertEquals(EditResult.Edited, edit)
            assertEquals("round $round", "Mine $round", shown.name)
            // The sync either won the race and its change was kept, or it lost and leaves the recipe to its next run.
            assertEquals("round $round", replaced, shown.json["description"] == JsonPrimitive("Web $round"))
        }
    }

    @Test
    fun `the name check ignores case and spaces and excludes the recipe itself`() = runTest {
        val soup = store.insert(synced("Linsensuppe", "1"))
        val deleted = store.insert(synced("Brot", "2"))
        repository.delete(deleted.localId)

        assertTrue(repository.isNameTaken("  linsenSUPPE "))
        assertFalse(repository.isNameTaken("Linsensuppe", exceptLocalId = soup.localId))
        assertFalse(repository.isNameTaken("Linsen"))
        assertFalse(repository.isNameTaken("Brot"))
    }

    @Test
    fun `categories and keywords follow the recipes`() = runTest {
        store.insert(synced("Soup", "1"))
        assertEquals(listOf("Soups"), repository.categories(Locale.GERMAN).first())

        val bread = recipe("Bread").with("recipeCategory", JsonPrimitive("Backen"))
        repository.create(bread.with("keywords", JsonPrimitive("Hefe, quick")))

        assertEquals(listOf("Backen", "Soups"), repository.categories(Locale.GERMAN).first())
        assertEquals(listOf("cheap", "Hefe", "quick"), repository.keywords(Locale.GERMAN).first())
    }

    private companion object {
        const val RACE_ROUNDS = 20
    }
}
