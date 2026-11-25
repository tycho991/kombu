package io.github.tycho991.kombu.data.recipes

import io.github.tycho991.kombu.cookbookclient.ImageSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
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

@RunWith(RobolectricTestRunner::class)
class RoomRecipeStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = inMemoryDatabase()
    private val images by lazy { ImageStore(File(folder.root, "images")) }
    private val store by lazy { RoomRecipeStore(database, images) }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun insertsAndListsRecipes() = runTest {
        val first = store.insert(synced("Soup", "1"))
        val second = store.insert(synced("Bread", "2").copy(localId = 77))

        assertEquals(listOf(first, second), store.recipes())
        assertTrue(second.localId != 77L)
    }

    @Test
    fun replacesOnlyTheExpectedVersion() = runTest {
        val stored = store.insert(synced("Soup", "1"))
        val changed = stored.copy(baseModified = "2024-05-01T10:00:00+0200")

        assertTrue(store.replace(stored, changed.copy(localId = 999)))
        assertFalse(store.replace(stored, stored.copy(baseModified = "other")))

        assertEquals(listOf(changed), store.recipes())
    }

    @Test
    fun removesOnlyTheExpectedVersionWithItsImages() = runTest {
        val stored = store.insert(synced("Soup", "1", image = "https://example.org/a.jpg"))
        store.saveImages(stored.localId, byteArrayOf(1), byteArrayOf(2))

        assertFalse(store.remove(stored.copy(baseModified = "other")))
        assertArrayEquals(byteArrayOf(1), images.file(stored.localId, ImageSize.FULL)?.readBytes())

        assertTrue(store.remove(stored))
        assertEquals(emptyList<LocalRecipe>(), store.recipes())
        assertNull(images.file(stored.localId, ImageSize.FULL))
    }

    @Test
    fun savesAndDeletesImages() = runTest {
        store.saveImages(5, byteArrayOf(1), byteArrayOf(2))

        assertArrayEquals(byteArrayOf(1), images.file(5, ImageSize.FULL)?.readBytes())
        assertArrayEquals(byteArrayOf(2), images.file(5, ImageSize.THUMB)?.readBytes())
        store.deleteImages(5)
        assertNull(images.file(5, ImageSize.THUMB))
    }

    @Test
    fun recordsSuccessAndProblems() = runTest {
        assertEquals(SyncState(), store.syncState())
        val repository = RecipeRepository(database, images)

        val problems =
            listOf(
                SyncProblem.Offline,
                SyncProblem.ServerError(502),
                SyncProblem.CookbookMissing,
                SyncProblem.CookbookTooOld("0.10.2"),
            )
        problems.forEach { problem ->
            store.recordProblem(problem)
            assertEquals(problem, repository.status().first().problem)
            assertFalse(store.syncState().credentialsRejected)
        }

        store.recordSuccess(4_000)
        assertNull(repository.status().first().problem)
        assertEquals(4_000L, repository.status().first().lastSuccessAt)

        store.recordProblem(SyncProblem.CredentialsRejected)
        assertTrue(store.syncState().credentialsRejected)
        assertEquals(SyncProblem.CredentialsRejected, repository.status().first().problem)
        assertEquals(4_000L, repository.status().first().lastSuccessAt)
    }
}
