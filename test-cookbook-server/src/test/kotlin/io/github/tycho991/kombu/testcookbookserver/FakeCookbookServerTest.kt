package io.github.tycho991.kombu.testcookbookserver

import io.github.tycho991.kombu.cookbookclient.CookbookClient
import io.github.tycho991.kombu.cookbookclient.ImageSize
import io.github.tycho991.kombu.cookbookclient.RecipeStub
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The fake server answers list, get, create, update, delete and images consistently, and injects failures. */
class FakeCookbookServerTest {
    private val server = FakeCookbookServer()
    private val web = MockWebServer()

    // Without retries, a dropped connection fails exactly the request it was meant for.
    private val http = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
    private val client = CookbookClient(http)
    private lateinit var credentials: NextcloudCredentials

    @BeforeEach
    fun startServer() {
        web.dispatcher = server
        web.start()
        credentials = NextcloudCredentials(ServerAddress.of(web.url("/nextcloud")), "alex", "app-password")
    }

    @AfterEach
    fun stopServer() {
        web.close()
    }

    private fun <T> NextcloudResult<T>.value(): T = (this as NextcloudResult.Success).value

    private fun remote(name: String) = RemoteRecipe(FakeCookbookServer.recipe(name))

    private fun RemoteRecipe.renamed(name: String) = with("name", JsonPrimitive(name))

    private fun JsonObject.renamed(name: String) = JsonObject(this + ("name" to JsonPrimitive(name)))

    /** Sends a raw request and returns the status code. */
    private fun raw(method: String, path: String, body: String? = null): Int {
        val request = Request.Builder()
            .url(web.url(FakeCookbookServer.PREFIX + path))
            .method(method, body?.toRequestBody("application/json".toMediaType()))
            .build()
        return http.newCall(request).execute().use { it.code }
    }

    @Test
    fun `serves the recipe lifecycle consistently`() = runTest {
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
            client.createRecipe(credentials, RemoteRecipe(JsonObject(emptyMap()))),
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
        assertEquals(5, server.bodies.size)
    }

    @Test
    fun `reports recipes without modification date`() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Old"), dated = false)

        assertNull(client.listRecipes(credentials).value().single().dateModified)
        assertNull(client.getRecipe(credentials, id).value().dateModified)
        server.edit(id) { it.renamed("Older") }
        assertNull(client.listRecipes(credentials).value().single().dateModified)
    }

    @Test
    fun `replaces and removes images`() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Cake"))
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1)

        server.setImage(id, png)
        assertArrayEquals(png, client.downloadImage(credentials, id, ImageSize.FULL).value())
        server.setImage(id, null)
        assertNull(client.downloadImage(credentials, id, ImageSize.FULL).value())
    }

    @Test
    fun `treats a create with an id as an update, like Cookbook`() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Soup"))
        val body = JsonObject(server.stored.getValue(id).renamed("Stew")).toString()

        assertEquals(200, raw("POST", FakeCookbookServer.RECIPES, body))
        assertEquals("Stew", server.stored.getValue(id)["name"]?.let { (it as JsonPrimitive).content })
        assertEquals(1, server.stored.size)
    }

    @Test
    fun `refuses invalid requests`() {
        val id = server.add(FakeCookbookServer.recipe("Soup"))
        val unnamed = buildJsonObject { put("id", id) }.toString()
        val unknown = buildJsonObject {
            put("id", "999")
            put("name", "Ghost")
        }.toString()

        assertEquals(422, raw("PUT", "${FakeCookbookServer.RECIPES}/$id", unnamed))
        assertEquals(500, raw("PUT", "${FakeCookbookServer.RECIPES}/999", unknown))
        assertEquals(405, raw("PATCH", "${FakeCookbookServer.RECIPES}/$id", "{}"))
        assertEquals(404, raw("GET", "index.php/apps/other"))
    }

    @Test
    fun `injects failures and logs requests`() = runTest {
        server.fail { it.method == "GET" }
        assertEquals(NextcloudResult.Failure(NextcloudError.ServerError(500)), client.listRecipes(credentials))
        server.heal()
        server.drop { true }
        assertEquals(NextcloudResult.Failure(NextcloudError.Unreachable), client.listRecipes(credentials))
        server.heal()
        server.version = null
        assertEquals(NextcloudResult.Failure(NextcloudError.NotFound), client.getRecipe(credentials, "1"))

        assertEquals(listOf(LIST, LIST, "GET ${FakeCookbookServer.RECIPES}/1"), server.log)
    }

    @Test
    fun `fails only as often as asked`() = runTest {
        server.fail(times = 1) { it.method == "GET" }

        assertEquals(NextcloudResult.Failure(NextcloudError.ServerError(500)), client.listRecipes(credentials))
        assertEquals(emptyList<RecipeStub>(), client.listRecipes(credentials).value())
    }

    @Test
    fun `serves a request before losing its answer`() = runTest {
        server.loseAnswer { it.method == "POST" }

        assertEquals(
            NextcloudResult.Failure(NextcloudError.Unreachable),
            client.createRecipe(credentials, remote("Pie")),
        )
        assertEquals(listOf("Pie"), client.listRecipes(credentials).value().map(RecipeStub::name))
        assertEquals(NextcloudResult.Failure(NextcloudError.NameTaken), client.createRecipe(credentials, remote("Pie")))
    }

    @Test
    fun `reports its version`() = runTest {
        assertEquals(200, raw("GET", "index.php/apps/cookbook/api/version"))
        server.version = null
        assertEquals(404, raw("GET", "index.php/apps/cookbook/api/version"))
    }

    @Test
    fun `writes dates the way Cookbook stores them`() {
        assertEquals("2024-01-01 10:01:05", FakeCookbookServer.rawDate(65))
    }

    private companion object {
        const val LIST = "GET ${FakeCookbookServer.RECIPES}"
    }
}
