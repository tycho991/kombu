package io.github.tycho991.kombu.data.recipes

import io.github.tycho991.kombu.cookbookclient.ImageSize
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RecipeRepositoryTest {
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

    private suspend fun insert(recipe: LocalRecipe): LocalRecipe = store.insert(recipe)

    private suspend fun withImages(recipe: LocalRecipe): LocalRecipe {
        store.saveImages(recipe.localId, byteArrayOf(1), byteArrayOf(2))
        return recipe
    }

    // Offline editing

    @Test
    fun createStoresAPendingRecipeWithoutId() = runTest {
        val localId = repository.create(recipe("Soup", id = "99"))

        val created = stored(localId)
        assertEquals(RecipeState.CREATED, created.state)
        assertNull(created.local?.id)
        assertEquals("Soup", created.shown?.name)
        assertEquals(1_000L, created.localChangeAt)
        assertEquals(1L, seq())
    }

    @Test
    fun editOfASyncedRecipeIsAPendingChangeThatKeepsUnknownFields() = runTest {
        val recipe = insert(synced("Soup", "1"))
        now = 2_000

        assertTrue(repository.edit(recipe.localId) { it.renamed("Lentil soup") })

        val edited = stored(recipe.localId)
        assertEquals(RecipeState.CHANGED, edited.state)
        assertEquals(recipe.base, edited.base)
        assertEquals("Lentil soup", edited.local?.name)
        assertEquals(recipe.base?.json?.minus("name"), edited.local?.json?.minus("name"))
        assertEquals(2_000L, edited.localChangeAt)
        assertEquals(1L, seq())
    }

    @Test
    fun aRepeatedEditKeepsOnePendingChange() = runTest {
        val recipe = insert(synced("Soup", "1"))

        repository.edit(recipe.localId) { it.renamed("Lentil soup") }
        repository.edit(recipe.localId) { it.with("recipeYield", JsonPrimitive(4)) }

        val edited = stored(recipe.localId)
        assertEquals(RecipeState.CHANGED, edited.state)
        assertEquals("Lentil soup", edited.local?.name)
        assertEquals(JsonPrimitive(4), edited.local?.json?.get("recipeYield"))
        assertEquals(1, repository.status().first().pendingCount)
        assertEquals(1, repository.recipes().first().size)
    }

    @Test
    fun anEditBackToTheSyncedVersionIsNoLongerPending() = runTest {
        val recipe = insert(synced("Soup", "1"))
        repository.edit(recipe.localId) { it.renamed("Lentil soup") }

        repository.edit(recipe.localId) { it.renamed("Soup") }

        assertEquals(RecipeState.SYNCED, stored(recipe.localId).state)
        assertNull(stored(recipe.localId).local)
    }

    @Test
    fun anEditWithoutChangeChangesNothing() = runTest {
        val recipe = insert(synced("Soup", "1"))

        assertTrue(repository.edit(recipe.localId) { it })

        assertEquals(recipe, stored(recipe.localId))
        assertEquals(0L, seq())
    }

    @Test
    fun editOfACreatedRecipeStaysCreatedAndClearsTheUploadError() = runTest {
        val recipe = insert(
            LocalRecipe(local = recipe("Soup"), state = RecipeState.CREATED, uploadError = UploadError.NAME_TAKEN),
        )

        repository.edit(recipe.localId) { it.renamed("Other soup") }

        val edited = stored(recipe.localId)
        assertEquals(RecipeState.CREATED, edited.state)
        assertEquals("Other soup", edited.local?.name)
        assertNull(edited.uploadError)
    }

    @Test
    fun editOfAConflictChangesTheLocalVersionOnly() = runTest {
        val recipe = insert(bothChanged())

        assertTrue(repository.edit(recipe.localId) { it.renamed("Mine again") })

        val edited = stored(recipe.localId)
        assertEquals(RecipeState.CONFLICT, edited.state)
        assertEquals("Mine again", edited.local?.name)
        assertEquals(recipe.server, edited.server)
    }

    @Test
    fun deletedAndMissingRecipesCannotBeEdited() = runTest {
        val deleted = insert(synced("Soup", "1").copy(state = RecipeState.DELETED))

        assertFalse(repository.edit(deleted.localId) { it.renamed("x") })
        assertFalse(repository.edit(12345) { it.renamed("x") })
        assertFalse(repository.edit(insert(deletedHereChangedOnServer()).localId) { it.renamed("x") })
    }

    @Test
    fun deleteOfACreatedRecipeRemovesIt() = runTest {
        val localId = repository.create(recipe("Soup"))
        withImages(stored(localId))

        assertTrue(repository.delete(localId))

        assertNull(repository.recipe(localId).first())
        assertNull(images.file(localId, ImageSize.FULL))
        assertEquals(2L, seq())
    }

    @Test
    fun deleteOfAChangedRecipeIsAPendingDeletion() = runTest {
        val recipe = insert(synced("Soup", "1"))
        repository.edit(recipe.localId) { it.renamed("Lentil soup") }

        assertTrue(repository.delete(recipe.localId))

        val deleted = stored(recipe.localId)
        assertEquals(RecipeState.DELETED, deleted.state)
        assertNull(deleted.local)
        assertEquals(recipe.base, deleted.base)
        assertEquals(emptyList<LocalRecipe>(), repository.recipes().first())
        assertEquals(1, repository.status().first().pendingCount)
        assertTrue(repository.delete(recipe.localId))
    }

    @Test
    fun conflictsAndMissingRecipesCannotBeDeleted() = runTest {
        assertFalse(repository.delete(insert(bothChanged()).localId))
        assertFalse(repository.delete(12345))
    }

    @Test
    fun statusCombinesCountsLastSyncAndProblem() = runTest {
        insert(synced("Soup", "1"))
        insert(bothChanged())
        repository.create(recipe("New"))
        store.recordSuccess(5_000)
        store.recordProblem(SyncProblem.ServerError(503))

        assertEquals(
            SyncStatus(
                recipeCount = 3,
                pendingCount = 1,
                conflictCount = 1,
                lastSuccessAt = 5_000,
                problem = SyncProblem.ServerError(503),
                localChangeSeq = 1,
            ),
            repository.status().first(),
        )
        assertEquals(2, repository.unsyncedCount())
    }

    @Test
    fun clearAllRemovesRowsImagesAndSyncState() = runTest {
        val recipe = withImages(insert(synced("Soup", "1", image = "https://example.org/a.jpg")))
        repository.create(recipe("New"))
        store.recordProblem(SyncProblem.CredentialsRejected)

        repository.clearAll()

        assertEquals(emptyList<LocalRecipe>(), store.recipes())
        assertNull(images.file(recipe.localId, ImageSize.FULL))
        assertFalse(File(folder.root, "images").exists())
        assertEquals(SyncState(), store.syncState())
        assertEquals(SyncStatus(), repository.status().first())
    }

    @Test
    fun acceptedCredentialsAllowSyncingAgain() = runTest {
        store.recordProblem(SyncProblem.CredentialsRejected)
        assertTrue(store.syncState().credentialsRejected)

        repository.credentialsAccepted()

        assertFalse(store.syncState().credentialsRejected)
        assertNull(repository.status().first().problem)
    }

    // Conflict resolution, one test per cell of the design D7 table

    private val server: RemoteRecipe = recipe("Soup", "9").renamed("Server soup")

    private fun bothChanged() = synced("Soup", "9").copy(
        local = recipe("Soup", "9").renamed("My soup"),
        state = RecipeState.CONFLICT,
        conflict = ConflictKind.BOTH_CHANGED,
        server = server,
        serverModified = "2024-03-01T10:00:00+0100",
    )

    private fun deletedOnServer() = synced("Soup", "9").copy(
        local = recipe("Soup", "9").renamed("My soup"),
        state = RecipeState.CONFLICT,
        conflict = ConflictKind.DELETED_ON_SERVER,
        imageState = ImageState.PRESENT,
    )

    private fun deletedHereChangedOnServer() = synced("Soup", "9").copy(
        state = RecipeState.CONFLICT,
        conflict = ConflictKind.CHANGED_ON_SERVER_DELETED_HERE,
        server = server.with("image", JsonPrimitive("https://example.org/new.jpg")),
        serverModified = "2024-03-01T10:00:00+0100",
    )

    @Test
    fun bothChangedKeepMineUploadsOnTopOfTheServerVersionTheUserSaw() = runTest {
        val seen = insert(bothChanged())
        now = 9_000

        assertTrue(repository.resolve(seen, Resolution.KEEP_MINE))

        val resolved = stored(seen.localId)
        assertEquals(RecipeState.CHANGED, resolved.state)
        assertEquals(seen.server, resolved.base)
        assertEquals("2024-03-01T10:00:00+0100", resolved.baseModified)
        assertEquals(seen.local, resolved.local)
        assertNull(resolved.conflict)
        assertNull(resolved.server)
        assertEquals(9_000L, resolved.localChangeAt)
        assertEquals(1L, seq())
    }

    @Test
    fun bothChangedTakeServerReplacesTheLocalVersion() = runTest {
        val seen = insert(bothChanged())

        assertTrue(repository.resolve(seen, Resolution.TAKE_SERVER))

        val resolved = stored(seen.localId)
        assertEquals(RecipeState.SYNCED, resolved.state)
        assertEquals(server, resolved.base)
        assertNull(resolved.local)
        assertNull(resolved.server)
        assertEquals(ImageState.NONE, resolved.imageState)
    }

    @Test
    fun deletedOnServerKeepMineCreatesItAgain() = runTest {
        val seen = withImages(insert(deletedOnServer()))

        assertTrue(repository.resolve(seen, Resolution.KEEP_MINE))

        val resolved = stored(seen.localId)
        assertEquals(RecipeState.CREATED, resolved.state)
        assertNull(resolved.remoteId)
        assertNull(resolved.base)
        assertEquals(seen.local, resolved.local)
        assertEquals(ImageState.NONE, resolved.imageState)
        assertNull(images.file(seen.localId, ImageSize.FULL))
    }

    @Test
    fun deletedOnServerTakeServerRemovesTheRecipe() = runTest {
        val seen = withImages(insert(deletedOnServer()))

        assertTrue(repository.resolve(seen, Resolution.TAKE_SERVER))

        assertNull(repository.recipe(seen.localId).first())
        assertNull(images.file(seen.localId, ImageSize.FULL))
    }

    @Test
    fun deletedHereKeepMineDeletesOnTopOfTheServerVersion() = runTest {
        val seen = insert(deletedHereChangedOnServer())

        assertTrue(repository.resolve(seen, Resolution.KEEP_MINE))

        val resolved = stored(seen.localId)
        assertEquals(RecipeState.DELETED, resolved.state)
        assertEquals(seen.server, resolved.base)
        assertNull(resolved.local)
    }

    @Test
    fun deletedHereTakeServerRestoresTheServerVersionAndItsImage() = runTest {
        val seen = insert(deletedHereChangedOnServer())

        assertTrue(repository.resolve(seen, Resolution.TAKE_SERVER))

        val resolved = stored(seen.localId)
        assertEquals(RecipeState.SYNCED, resolved.state)
        assertEquals(seen.server, resolved.base)
        assertEquals(ImageState.MISSING, resolved.imageState)
    }

    @Test
    fun aResolutionOfAnOutdatedConflictChangesNothing() = runTest {
        val seen = insert(bothChanged())
        val newer = seen.copy(server = server.renamed("Even newer soup"))
        assertTrue(store.replace(seen, newer))

        assertFalse(repository.resolve(seen, Resolution.KEEP_MINE))

        assertEquals(newer, stored(seen.localId))
        assertEquals(0L, seq())
    }

    @Test
    fun onlyConflictsCanBeResolved() = runTest {
        val recipe = insert(synced("Soup", "1"))

        assertFalse(repository.resolve(recipe, Resolution.TAKE_SERVER))
        assertNotNull(repository.recipe(recipe.localId).first())
    }
}
