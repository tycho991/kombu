package io.github.tycho991.kombu.sync

import io.github.tycho991.kombu.cookbookclient.CookbookClient
import io.github.tycho991.kombu.cookbookclient.ImageSize
import io.github.tycho991.kombu.cookbookclient.RecipeStub
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 5.1: the fake server answers list, get, create, update, delete and images consistently. */
class FakeCookbookServerTest : SyncTestBase() {
    private val client = CookbookClient(OkHttpClient.Builder().retryOnConnectionFailure(false).build())

    private fun <T> NextcloudResult<T>.value(): T = (this as NextcloudResult.Success).value

    @Test
    fun servesTheRecipeLifecycleConsistently() = runTest {
        val soup = server.add(FakeCookbookServer.recipe("Soup"), image = true)

        val stubs = client.listRecipes(credentials).value()
        assertEquals(listOf(soup), stubs.map(RecipeStub::id))
        assertTrue(stubs.single().dateModified!!.endsWith("+0000"))

        val read = client.getRecipe(credentials, soup).value()
        assertEquals("Soup", read.name)
        assertEquals(JsonPrimitive(true), read.json["printImage"])
        assertNotEquals(stubs.single().dateModified, read.dateModified)

        val created = client.createRecipe(credentials, remote("Bread")).value()
        assertEquals(
            NextcloudResult.Failure(NextcloudError.NameTaken),
            client.createRecipe(credentials, remote("Bread")),
        )
        assertEquals(
            NextcloudResult.Failure(NextcloudError.NameMissing),
            client.createRecipe(credentials, RemoteRecipe(kotlinx.serialization.json.JsonObject(emptyMap()))),
        )

        val before = server.stored.getValue(created)["dateModified"]
        client.updateRecipe(
            credentials,
            created,
            client.getRecipe(credentials, created).value().renamed("Rye bread"),
        ).value()
        assertEquals("Rye bread", client.getRecipe(credentials, created).value().name)
        assertNotEquals(before, server.stored.getValue(created)["dateModified"])
        assertNull(server.stored.getValue(created)["printImage"])
        assertEquals(
            NextcloudResult.Failure(NextcloudError.NameTaken),
            client.updateRecipe(credentials, created, client.getRecipe(credentials, created).value().renamed("Soup")),
        )

        assertArrayEquals(
            FakeCookbookServer.jpeg(soup),
            client.downloadImage(credentials, soup, ImageSize.FULL).value(),
        )
        assertNull(client.downloadImage(credentials, created, ImageSize.THUMB).value())

        client.deleteRecipe(credentials, soup).value()
        assertEquals(NextcloudResult.Failure(NextcloudError.NotFound), client.getRecipe(credentials, soup))
        assertEquals(listOf(created), client.listRecipes(credentials).value().map(RecipeStub::id))
    }

    @Test
    fun reportsRecipesWithoutModificationDate() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Old"), dated = false)

        assertNull(client.listRecipes(credentials).value().single().dateModified)
        assertNull(client.getRecipe(credentials, id).value().dateModified)
        server.edit(id) { it.renamed("Older") }
        assertNull(client.listRecipes(credentials).value().single().dateModified)
    }

    @Test
    fun injectsFailuresAndLogsRequests() = runTest {
        server.fail { it.method == "GET" }
        assertEquals(NextcloudResult.Failure(NextcloudError.ServerError(500)), client.listRecipes(credentials))
        server.heal()
        server.drop { true }
        assertEquals(NextcloudResult.Failure(NextcloudError.Unreachable), client.listRecipes(credentials))
        server.heal()
        server.version = null
        assertEquals(NextcloudResult.Failure(NextcloudError.NotFound), client.getRecipe(credentials, "1"))

        assertEquals(listOf(LIST, LIST, get("1")), server.log)
    }
}
