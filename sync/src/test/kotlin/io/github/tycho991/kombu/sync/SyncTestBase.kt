package io.github.tycho991.kombu.sync

import io.github.tycho991.kombu.cookbookclient.CookbookClient
import io.github.tycho991.kombu.cookbookclient.RecipeHash
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import io.github.tycho991.kombu.testcookbookserver.FakeCookbookServer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before

/** A sync engine against a [FakeCookbookServer] and an [InMemoryRecipeStore], on the plain JVM (design D15). */
abstract class SyncTestBase {
    protected val server = FakeCookbookServer()
    protected val store = InMemoryRecipeStore()
    private val web = MockWebServer()
    protected var now = 1_000L
    protected var signedIn = true
    protected lateinit var credentials: NextcloudCredentials
    protected lateinit var engine: SyncEngine

    @Before
    fun startServer() {
        web.dispatcher = server
        web.start()
        credentials = NextcloudCredentials(ServerAddress.of(web.url("/nextcloud")), "alex", "app-password")
        // Without retries, a dropped connection fails exactly the request it was meant for.
        val client = CookbookClient(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
        engine = SyncEngine(client, store, { credentials.takeIf { signedIn } }) { now }
    }

    @After
    fun stopServer() {
        web.close()
    }

    /** Runs a sync and returns only the requests it sent. */
    protected suspend fun sync(): Pair<SyncResult, List<String>> {
        val before = server.log.size
        val result = engine.run()
        return result to server.log.drop(before)
    }

    protected fun remote(name: String, image: String? = null) = RemoteRecipe(FakeCookbookServer.recipe(name, image))

    protected fun RemoteRecipe.renamed(name: String) = with("name", JsonPrimitive(name))

    protected fun JsonObject.renamed(name: String) = JsonObject(this + ("name" to JsonPrimitive(name)))

    /** The content of [local]'s shown version equals the server recipe [id]. */
    protected fun assertSameContent(id: String, local: LocalRecipe?) {
        assertEquals(RecipeHash.of(server.stored.getValue(id)), local?.shown?.let(RecipeHash::of))
    }

    protected companion object {
        const val VERSION = "GET index.php/apps/cookbook/api/version"
        const val LIST = "GET index.php/apps/cookbook/api/v1/recipes"

        fun get(id: String) = "GET index.php/apps/cookbook/api/v1/recipes/$id"

        fun put(id: String) = "PUT index.php/apps/cookbook/api/v1/recipes/$id"

        fun delete(id: String) = "DELETE index.php/apps/cookbook/api/v1/recipes/$id"

        fun image(id: String) = "GET index.php/apps/cookbook/api/v1/recipes/$id/image"

        const val POST = "POST index.php/apps/cookbook/api/v1/recipes"
    }
}
