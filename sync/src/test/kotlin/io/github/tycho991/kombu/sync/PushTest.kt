package io.github.tycho991.kombu.sync

import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.SyncProblem
import io.github.tycho991.kombu.data.recipes.UploadError
import io.github.tycho991.kombu.testcookbookserver.FakeCookbookServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 5.3: push, one test per scenario of the spec requirements named in the comments. */
class PushTest : SyncTestBase() {
    private suspend fun syncedRecipe(name: String): Pair<String, LocalRecipe> {
        val id = server.add(FakeCookbookServer.recipe(name))
        sync()
        return id to store.byRemoteId(id)!!
    }

    private fun requestsWriting(requests: List<String>) =
        requests.filter { it.startsWith("PUT") || it.startsWith("POST") || it.startsWith("DELETE") }

    // Pending changes are uploaded

    @Test
    fun offlineChangeIsUploadedAndNoLongerPending() = runTest {
        val (id, local) = syncedRecipe("Soup")
        store.edit(local.localId) { it.renamed("Lentil soup") }

        val (result, requests) = sync()

        assertEquals(SyncResult.Success, result)
        assertTrue(put(id) in requests)
        assertEquals("Lentil soup", server.stored.getValue(id)["name"]?.let { (it as JsonPrimitive).content })
        val synced = store.byRemoteId(id)
        assertEquals(RecipeState.SYNCED, synced?.state)
        assertNull(synced?.local)
        assertSameContent(id, synced)
    }

    @Test
    fun newRecipeIsCreatedAndLinked() = runTest {
        val created = store.create(remote("Bread"))

        val (_, requests) = sync()

        assertTrue(POST in requests)
        val id = server.stored.entries.single { (it.value["name"] as JsonPrimitive).content == "Bread" }.key
        val linked = store.all.single()
        assertEquals(created.localId, linked.localId)
        assertEquals(id, linked.remoteId)
        assertEquals(RecipeState.SYNCED, linked.state)
        assertSameContent(id, linked)
    }

    @Test
    fun createdAndDeletedBeforeUploadSendsNothing() = runTest {
        val created = store.create(remote("Bread"))
        store.delete(created.localId)

        val (_, requests) = sync()

        assertEquals(emptyList<String>(), requestsWriting(requests))
        assertEquals(emptyList<LocalRecipe>(), store.all)
    }

    @Test
    fun deletionIsUploaded() = runTest {
        val (id, local) = syncedRecipe("Soup")
        store.delete(local.localId)

        val (_, requests) = sync()

        assertTrue(delete(id) in requests)
        assertTrue(id !in server.stored)
        assertEquals(emptyList<LocalRecipe>(), store.all)
    }

    @Test
    fun pendingChangesAreUploadedInTheOrderTheyWereMade() = runTest {
        val (first, a) = syncedRecipe("A")
        val (second, b) = syncedRecipe("B")
        store.edit(b.localId, at = 1) { it.renamed("B2") }
        store.edit(a.localId, at = 2) { it.renamed("A2") }

        val (_, requests) = sync()

        assertEquals(listOf(put(second), put(first)), requestsWriting(requests))
    }

    // Unknown fields are kept

    @Test
    fun fieldsKombuDoesNotShowSurviveTheUpload() = runTest {
        val (id, local) = syncedRecipe("Soup")
        val original = server.stored.getValue(id)
        store.edit(local.localId) { it.renamed("Lentil soup") }

        sync()

        val body = server.bodies.last()
        assertEquals(JsonPrimitive("Lentil soup"), body["name"])
        listOf("author", "nutrition", "recipeIngredient", "@type").forEach { field ->
            assertEquals(field, original[field], body[field])
        }
    }

    // Check before upload

    @Test
    fun changedInBothPlacesUploadsNothingAndBecomesAConflict() = runTest {
        val (id, local) = syncedRecipe("Soup")
        val mine = store.edit(local.localId) { it.renamed("My soup") }
        server.edit(id) { it.renamed("Their soup") }

        val (_, requests) = sync()

        assertEquals(emptyList<String>(), requestsWriting(requests))
        val conflict = store.byRemoteId(id)
        assertEquals(RecipeState.CONFLICT, conflict?.state)
        assertEquals(ConflictKind.BOTH_CHANGED, conflict?.conflict)
        assertEquals(mine.local, conflict?.local)
        assertEquals("Their soup", conflict?.server?.name)
        assertEquals("Their soup", (server.stored.getValue(id)["name"] as JsonPrimitive).content)
    }

    @Test
    fun changedOnlyInKombuIsUploadedAfterTheCheck() = runTest {
        val (id, local) = syncedRecipe("Soup")
        store.edit(local.localId) { it.renamed("My soup") }

        val (_, requests) = sync()

        assertEquals(listOf(get(id), put(id), get(id)), requests.drop(2))
    }

    @Test
    fun deletedHereButChangedOnTheServerIsNotDeleted() = runTest {
        val (id, local) = syncedRecipe("Soup")
        store.delete(local.localId)
        server.edit(id) { it.renamed("Their soup") }

        val (_, requests) = sync()

        assertEquals(emptyList<String>(), requestsWriting(requests))
        assertTrue(id in server.stored)
        val conflict = store.byRemoteId(id)
        assertEquals(ConflictKind.CHANGED_ON_SERVER_DELETED_HERE, conflict?.conflict)
        assertEquals("Their soup", conflict?.server?.name)
    }

    @Test
    fun deletedHereAndOnTheServerIsSimplyGone() = runTest {
        val (id, local) = syncedRecipe("Soup")
        store.delete(local.localId)
        server.delete(id)

        val (_, requests) = sync()

        assertEquals(emptyList<String>(), requestsWriting(requests))
        assertEquals(emptyList<LocalRecipe>(), store.all)
    }

    // Conflicts keep both versions

    @Test
    fun syncWithAnOpenConflictKeepsBothVersionsAndRefreshesTheServerOne() = runTest {
        val (id, local) = syncedRecipe("Soup")
        val mine = store.edit(local.localId) { it.renamed("My soup") }
        server.edit(id) { it.renamed("Their soup") }
        sync()
        server.edit(id) { it.renamed("Their newer soup") }

        val (_, requests) = sync()

        assertEquals(emptyList<String>(), requestsWriting(requests))
        val conflict = store.byRemoteId(id)
        assertEquals(mine.local, conflict?.local)
        assertEquals("Their newer soup", conflict?.server?.name)

        val (_, quiet) = sync()
        assertEquals(listOf(VERSION, LIST), quiet)
    }

    @Test
    fun conflictWhoseServerRecipeIsDeletedBecomesADeletionConflict() = runTest {
        val (id, local) = syncedRecipe("Soup")
        store.edit(local.localId) { it.renamed("My soup") }
        server.edit(id) { it.renamed("Their soup") }
        sync()
        server.delete(id)

        sync()

        assertEquals(ConflictKind.DELETED_ON_SERVER, store.byRemoteId(id)?.conflict)
        assertNull(store.byRemoteId(id)?.server)
    }

    // Resolving a conflict (engine side)

    @Test
    fun keepMineIsUploadedWhenTheServerStillHasTheVersionTheUserSaw() = runTest {
        val (id, local) = syncedRecipe("Soup")
        store.edit(local.localId) { it.renamed("My soup") }
        server.edit(id) { it.renamed("Their soup") }
        sync()
        val conflict = store.byRemoteId(id)!!
        store.put(conflict.copy(base = conflict.server, state = RecipeState.CHANGED, conflict = null, server = null))

        sync()

        assertEquals("My soup", (server.stored.getValue(id)["name"] as JsonPrimitive).content)
        assertEquals(RecipeState.SYNCED, store.byRemoteId(id)?.state)
    }

    @Test
    fun keepMineBecomesANewConflictWhenTheServerMovedAgain() = runTest {
        val (id, local) = syncedRecipe("Soup")
        store.edit(local.localId) { it.renamed("My soup") }
        server.edit(id) { it.renamed("Their soup") }
        sync()
        val conflict = store.byRemoteId(id)!!
        store.put(conflict.copy(base = conflict.server, state = RecipeState.CHANGED, conflict = null, server = null))
        server.edit(id) { it.renamed("Their newest soup") }

        val (_, requests) = sync()

        assertEquals(emptyList<String>(), requestsWriting(requests))
        assertEquals("Their newest soup", store.byRemoteId(id)?.server?.name)
    }

    @Test
    fun keepingARecipeDeletedOnTheServerCreatesItAgain() = runTest {
        val (id, local) = syncedRecipe("Soup")
        val mine = store.edit(local.localId) { it.renamed("My soup") }
        server.delete(id)
        sync()
        val conflict = store.byRemoteId(id)!!
        store.put(conflict.copy(remoteId = null, base = null, state = RecipeState.CREATED, conflict = null))

        val (_, requests) = sync()

        assertTrue(POST in requests)
        val recreated = store.all.single()
        assertEquals(mine.localId, recreated.localId)
        assertEquals(RecipeState.SYNCED, recreated.state)
        assertTrue(recreated.remoteId != id)
        assertNull(server.bodies.last()["id"])
    }

    // Rejected uploads keep the local change

    @Test
    fun nameAlreadyTakenStaysLocalAndTheOthersAreUploaded() = runTest {
        server.add(FakeCookbookServer.recipe("Soup"))
        val (bread, local) = syncedRecipe("Bread")
        val duplicate = store.create(remote("Soup").with("description", JsonPrimitive("mine")), at = 1)
        store.edit(local.localId, at = 2) { it.renamed("Rye bread") }

        assertEquals(SyncResult.Success, sync().first)

        val refused = store.all.single { it.localId == duplicate.localId }
        assertEquals(RecipeState.CREATED, refused.state)
        assertEquals(UploadError.NAME_TAKEN, refused.uploadError)
        assertEquals(duplicate.local, refused.local)
        assertEquals("Rye bread", (server.stored.getValue(bread)["name"] as JsonPrimitive).content)
    }

    @Test
    fun missingNameAndOtherRefusalsAreRecorded() = runTest {
        val unnamed = store.create(remote("x").without("name"))
        val (id, local) = syncedRecipe("Soup")
        store.edit(local.localId) { it.renamed("Big soup") }
        server.fail(MockResponse.Builder().code(400).build()) { it.method == "PUT" }

        assertEquals(SyncResult.Success, sync().first)

        assertEquals(UploadError.NAME_MISSING, store.all.single { it.localId == unnamed.localId }.uploadError)
        assertEquals(UploadError.REJECTED, store.byRemoteId(id)?.uploadError)
        assertEquals(RecipeState.CHANGED, store.byRemoteId(id)?.state)
    }

    @Test
    fun renamingToATakenNameIsRefused() = runTest {
        server.add(FakeCookbookServer.recipe("Stew"))
        val (id, local) = syncedRecipe("Soup")
        store.edit(local.localId) { it.renamed("Stew") }

        sync()

        assertEquals(UploadError.NAME_TAKEN, store.byRemoteId(id)?.uploadError)
    }

    @Test
    fun aServerErrorWhileUploadingStopsTheRunAndKeepsTheChange() = runTest {
        val (id, local) = syncedRecipe("Soup")
        val mine = store.edit(local.localId) { it.renamed("My soup") }
        server.fail { it.method == "PUT" }

        val result = sync().first

        assertEquals(SyncResult.Failed(SyncProblem.ServerError(500)), result)
        assertTrue((result as SyncResult.Failed).retry)
        assertEquals(mine, store.byRemoteId(id))
    }

    // Lost answers (design D6, D11)

    @Test
    fun aCreateWhoseAnswerWasLostIsRecognisedAsKombusOwn() = runTest {
        store.create(remote("Bread"))
        server.loseAnswer { it.method == "POST" }

        assertEquals(SyncResult.Failed(SyncProblem.Offline), sync().first)
        assertEquals(SyncResult.Success, sync().first)

        val only = store.all.single()
        assertEquals(RecipeState.SYNCED, only.state)
        assertEquals(1, server.stored.size)
        assertSameContent(only.remoteId!!, only)
    }

    @Test
    fun aCreateWhoseAnswerWasLostIsLinkedWhenThePullMissedIt() = runTest {
        val created = store.create(remote("Bread"))
        server.loseAnswer { it.method == "POST" }
        sync()
        val id = server.stored.keys.single()
        server.fail(MockResponse.Builder().code(404).build(), times = 1) { it.url.encodedPath.endsWith("/$id") }

        sync()

        val linked = store.all.single()
        assertEquals(created.localId, linked.localId)
        assertEquals(id, linked.remoteId)
        assertEquals(RecipeState.SYNCED, linked.state)
    }

    @Test
    fun anUpdateWhoseAnswerWasLostIsNoConflict() = runTest {
        val (id, local) = syncedRecipe("Soup")
        store.edit(local.localId) { it.renamed("My soup") }
        server.loseAnswer { it.method == "PUT" }

        assertEquals(SyncResult.Failed(SyncProblem.Offline), sync().first)
        val (_, requests) = sync()

        assertEquals(emptyList<String>(), requestsWriting(requests))
        assertEquals(RecipeState.SYNCED, store.byRemoteId(id)?.state)
        assertSameContent(id, store.byRemoteId(id))
    }

    @Test
    fun theSameEditOnBothSidesIsNoConflict() = runTest {
        val (id, local) = syncedRecipe("Soup")
        store.edit(local.localId) { it.renamed("Same soup") }
        server.edit(id) { it.renamed("Same soup") }

        val (_, requests) = sync()

        assertEquals(emptyList<String>(), requestsWriting(requests))
        assertEquals(RecipeState.SYNCED, store.byRemoteId(id)?.state)
    }

    // Local changes during an upload

    @Test
    fun anEditDuringTheUploadIsUploadedOnTopOfIt() = runTest {
        val (id, local) = syncedRecipe("Soup")
        store.edit(local.localId) { it.renamed("My soup") }
        var editedMeanwhile = false
        store.beforeWrite = {
            if (!editedMeanwhile && server.log.last() == get(id) && put(id) in server.log) {
                editedMeanwhile = true
                store.edit(local.localId) { it.renamed("My better soup") }
            }
        }

        val (_, requests) = sync()
        store.beforeWrite = {}

        // The first upload became the base of the newer edit, which the re-run uploaded without a conflict.
        assertEquals(listOf(put(id), put(id)), requestsWriting(requests))
        assertEquals("My better soup", (server.stored.getValue(id)["name"] as JsonPrimitive).content)
        assertEquals(RecipeState.SYNCED, store.byRemoteId(id)?.state)
    }

    @Test
    fun aRecipeDeletedDuringItsFirstUploadIsDeletedOnTheServerNext() = runTest {
        val created = store.create(remote("Bread"))
        var deletedMeanwhile = false
        store.beforeWrite = {
            if (!deletedMeanwhile && server.log.last().startsWith("GET") && server.log.any { it == POST }) {
                deletedMeanwhile = true
                store.delete(created.localId)
            }
        }

        sync()
        store.beforeWrite = {}

        assertEquals(emptyList<LocalRecipe>(), store.all)
        assertEquals(emptyMap<String, Any>(), server.stored)
    }
}
