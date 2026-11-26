package io.github.tycho991.kombu.sync

import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.data.recipes.ImageState
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.SyncProblem
import io.github.tycho991.kombu.testcookbookserver.FakeCookbookServer
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 5.2: check and pull, one test per scenario of the spec requirements named in the test names. */
class PullTest : SyncTestBase() {
    // Complete local copy

    @Test
    fun firstSyncStoresEveryRecipeAndEveryImage() = runTest {
        val soup = server.add(FakeCookbookServer.recipe("Soup", image = "https://example.org/soup.jpg"), image = true)
        val bread = server.add(FakeCookbookServer.recipe("Bread"))
        val stew = server.add(FakeCookbookServer.recipe("Stew", image = "https://example.org/stew.jpg"), image = true)

        assertEquals(SyncResult.Success, sync().first)

        assertEquals(3, store.all.size)
        listOf(soup, bread, stew).forEach { id ->
            val local = store.byRemoteId(id)
            assertEquals(RecipeState.SYNCED, local?.state)
            assertSameContent(id, local)
        }
        assertArrayEquals(FakeCookbookServer.jpeg(soup), store.images[store.byRemoteId(soup)!!.localId]?.first)
        assertArrayEquals(FakeCookbookServer.jpeg(stew), store.images[store.byRemoteId(stew)!!.localId]?.second)
        assertEquals(ImageState.PRESENT, store.byRemoteId(soup)?.imageState)
        assertEquals(ImageState.NONE, store.byRemoteId(bread)?.imageState)
        assertEquals(1_000L, store.lastSuccessAt)
        assertNull(store.problem)
    }

    @Test
    fun keepsUnknownFieldsAndTheirTypes() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Soup"))

        sync()

        val base = store.byRemoteId(id)?.base?.json
        assertEquals(server.stored.getValue(id)["author"], base?.get("author"))
        assertEquals(server.stored.getValue(id)["nutrition"], base?.get("nutrition"))
    }

    // Supported Cookbook versions

    @Test
    fun supportedVersionProceeds() = runTest {
        server.add(FakeCookbookServer.recipe("Soup"))

        val (result, requests) = sync()

        assertEquals(SyncResult.Success, result)
        assertEquals(VERSION, requests.first())
    }

    @Test
    fun versionTooOldStopsBeforeAnyChange() = runTest {
        server.add(FakeCookbookServer.recipe("Soup"))
        server.version = listOf(0, 10, 2)

        val (result, requests) = sync()

        assertEquals(SyncResult.Failed(SyncProblem.CookbookTooOld("0.10.2")), result)
        assertEquals(listOf(VERSION), requests)
        assertEquals(emptyList<Any>(), store.all)
        assertEquals(SyncProblem.CookbookTooOld("0.10.2"), store.problem)
        assertEquals(false, (result as SyncResult.Failed).retry)
    }

    @Test
    fun unknownVersionStops() = runTest {
        server.version = null

        val (result, requests) = sync()

        assertEquals(SyncResult.Failed(SyncProblem.CookbookMissing), result)
        assertEquals(listOf(VERSION, "GET ocs/v2.php/cloud/capabilities"), requests.map { it.substringBefore('?') })
    }

    @Test
    fun notSignedInDoesNothing() = runTest {
        signedIn = false

        assertEquals(SyncResult.NotSignedIn to emptyList<String>(), sync())
    }

    // Server changes are downloaded

    @Test
    fun recipeChangedOnTheServerReplacesTheLocalCopy() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Soup"))
        sync()
        server.edit(id) { it.renamed("Lentil soup") }

        sync()

        val local = store.byRemoteId(id)
        assertEquals("Lentil soup", local?.shown?.name)
        assertEquals(RecipeState.SYNCED, local?.state)
        assertSameContent(id, local)
    }

    @Test
    fun newRecipeOnTheServerIsDownloadedWithItsImage() = runTest {
        sync()
        val id = server.add(FakeCookbookServer.recipe("Soup", image = "https://example.org/soup.jpg"), image = true)

        val (_, requests) = sync()

        assertTrue(requests.containsAll(listOf(get(id), "${image(id)}")))
        assertEquals(ImageState.PRESENT, store.byRemoteId(id)?.imageState)
        assertNotNull(store.images[store.byRemoteId(id)!!.localId])
    }

    @Test
    fun recipeWithoutModificationDateIsDownloadedAndKeptWhenEqual() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Old"), dated = false)
        sync()
        val before = store.byRemoteId(id)

        val (_, requests) = sync()

        assertTrue(get(id) in requests)
        assertEquals(before, store.byRemoteId(id))
    }

    @Test
    fun recipeWithoutModificationDateTakesAServerChange() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Old"), dated = false)
        sync()
        server.edit(id) { it.renamed("Older") }

        sync()

        assertEquals("Older", store.byRemoteId(id)?.shown?.name)
    }

    @Test
    fun nothingChangedDownloadsNoRecipeAndNoImage() = runTest {
        server.add(FakeCookbookServer.recipe("Soup", image = "https://example.org/soup.jpg"), image = true)
        server.add(FakeCookbookServer.recipe("Bread"))
        sync()

        val (result, requests) = sync()

        assertEquals(SyncResult.Success, result)
        assertEquals(listOf(VERSION, LIST), requests)
    }

    // Recipe images

    @Test
    fun recipeWithoutImageGetsNoPlaceholder() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Bread"))

        val (_, requests) = sync()

        assertEquals(ImageState.NONE, store.byRemoteId(id)?.imageState)
        assertTrue(requests.none { it.startsWith(image(id)) })
        assertTrue(store.images.isEmpty())
    }

    @Test
    fun recipeWhoseImageFileIsMissingOnTheServerHasNone() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Soup", image = "https://example.org/gone.jpg"))

        sync()

        assertEquals(ImageState.NONE, store.byRemoteId(id)?.imageState)
        assertTrue(store.images.isEmpty())
    }

    @Test
    fun failedImageDownloadKeepsTheRecipeAndIsRetried() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Soup", image = "https://example.org/soup.jpg"), image = true)
        server.fail { it.url.encodedPath.endsWith("/image") }

        assertEquals(SyncResult.Success, sync().first)
        assertEquals(ImageState.MISSING, store.byRemoteId(id)?.imageState)
        assertSameContent(id, store.byRemoteId(id))

        server.heal()
        val (_, requests) = sync()
        assertTrue(requests.any { it.startsWith(image(id)) })
        assertEquals(ImageState.PRESENT, store.byRemoteId(id)?.imageState)
    }

    @Test
    fun changedRecipeGetsItsImageAgainAndLosesARemovedOne() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Soup", image = "https://example.org/a.jpg"), image = true)
        sync()
        server.setImage(id, FakeCookbookServer.jpeg("new"))
        server.edit(id) { it.renamed("Soup 2") }

        sync()
        assertArrayEquals(FakeCookbookServer.jpeg("new"), store.images[store.byRemoteId(id)!!.localId]?.first)

        server.setImage(id, null)
        server.edit(
            id,
        ) { kotlinx.serialization.json.JsonObject(FakeCookbookServer.recipe("Soup 3") + ("id" to it.getValue("id"))) }
        sync()
        assertEquals(ImageState.NONE, store.byRemoteId(id)?.imageState)
        assertTrue(store.images.isEmpty())
    }

    // Server deletions

    @Test
    fun deletedOnTheServerAndUnchangedHereIsRemoved() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Soup", image = "https://example.org/soup.jpg"), image = true)
        sync()
        server.delete(id)

        sync()

        assertNull(store.byRemoteId(id))
        assertTrue(store.images.isEmpty())
    }

    @Test
    fun deletedOnTheServerAndChangedHereBecomesAConflict() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Soup"))
        sync()
        val local = store.edit(store.byRemoteId(id)!!.localId) { it.renamed("My soup") }
        server.delete(id)

        val (_, requests) = sync()

        val conflict = store.byRemoteId(id)
        assertEquals(RecipeState.CONFLICT, conflict?.state)
        assertEquals(ConflictKind.DELETED_ON_SERVER, conflict?.conflict)
        assertEquals(local.local, conflict?.local)
        assertNull(conflict?.server)
        assertTrue(requests.none { it.startsWith("PUT") || it.startsWith("POST") })
    }

    // Rejected credentials stop the sync

    @Test
    fun revokedAppPasswordStopsTheSyncAndKeepsLocalData() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Soup"))
        sync()
        store.edit(store.byRemoteId(id)!!.localId) { it.renamed("My soup") }
        val before = store.all
        server.fail(MockResponse.Builder().code(401).build()) { true }

        val result = sync().first

        assertEquals(SyncResult.Failed(SyncProblem.CredentialsRejected), result)
        assertEquals(false, (result as SyncResult.Failed).retry)
        assertEquals(before, store.all)
        assertEquals(SyncProblem.CredentialsRejected, store.problem)
        assertEquals(1_000L, store.lastSuccessAt)
    }

    @Test
    fun rejectedCredentialsAreNotTriedAgain() = runTest {
        server.fail(MockResponse.Builder().code(401).build()) { true }
        sync()
        server.heal()

        val (result, requests) = sync()

        assertEquals(SyncResult.Failed(SyncProblem.CredentialsRejected), result)
        assertEquals(emptyList<String>(), requests)
    }
}
