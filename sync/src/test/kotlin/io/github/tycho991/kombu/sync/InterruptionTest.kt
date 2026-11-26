package io.github.tycho991.kombu.sync

import io.github.tycho991.kombu.cookbookclient.CookbookClient
import io.github.tycho991.kombu.cookbookclient.RecipeHash
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import io.github.tycho991.kombu.testcookbookserver.FakeCookbookServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 5.4 (design D15): a sync is interrupted at every request position, once before the server handles the
 * request and once after (the answer is lost). No interrupted run may lose a pending change, and a clean run
 * afterwards must reach the same end state as a sync that was never interrupted.
 */
private fun JsonObject.renamed(name: String) = JsonObject(this + ("name" to JsonPrimitive(name)))

private fun RemoteRecipe.renamed(name: String) = with("name", JsonPrimitive(name))

class InterruptionTest {
    /** A server, a store and an engine, set up with every kind of pending work. */
    private class World : AutoCloseable {
        val server = FakeCookbookServer()
        val store = InMemoryRecipeStore()
        private val web = MockWebServer().apply {
            dispatcher = server
            start()
        }
        private val credentials = NextcloudCredentials(ServerAddress.of(web.url("/nextcloud")), "alex", "secret")
        private val client = CookbookClient(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
        val engine = SyncEngine(client, store, { credentials }) { 1_000 }
        private val ids = mutableMapOf<String, String>()

        suspend fun prepare() {
            listOf("Apple pie", "Bread", "Cake", "Dal", "Eggs", "Gnocchi").forEach { name ->
                ids[name] = server.add(
                    FakeCookbookServer.recipe(name, image = "https://example.org/$name.jpg"),
                    image = true,
                )
            }
            check(engine.run() == SyncResult.Success)
            server.edit(id("Bread")) { it.renamed("Bread 2") }
            server.delete(id("Cake"))
            store.edit(local("Dal"), at = 1) { it.renamed("Dal 2") }
            store.delete(local("Eggs"), at = 2)
            store.create(RemoteRecipe(FakeCookbookServer.recipe("Fig jam")), at = 3)
            store.edit(local("Gnocchi"), at = 4) { it.renamed("Gnocchi mine") }
            server.edit(id("Gnocchi")) { it.renamed("Gnocchi theirs") }
            server.add(FakeCookbookServer.recipe("Ham", image = "https://example.org/ham.jpg"), image = true)
        }

        fun id(name: String) = ids.getValue(name)

        fun local(name: String) = checkNotNull(store.byRemoteId(id(name))).localId

        /** Everything that must match: local rows by shown name, and the server's recipes by name. */
        fun endState(): Pair<Set<String>, Map<String, String>> {
            val local =
                store.all
                    .map { row ->
                        listOf(
                            row.shown?.name,
                            row.state,
                            row.conflict,
                            row.shown?.let(RecipeHash::of),
                            row.server?.let(RecipeHash::of),
                            row.imageState,
                            row.uploadError,
                        ).joinToString()
                    }.toSet()
            val remote = server.stored.values.associate { it.name() to RecipeHash.of(it) }
            return local to remote
        }

        /** The pending changes made in [prepare] are still pending here or already on the server. */
        fun assertNothingLost() {
            val names = server.stored.values.map { it.name() }
            val dal = store.all.any { it.local?.name == "Dal 2" && it.state == RecipeState.CHANGED }
            assertTrue("Dal edit", dal || "Dal 2" in names)
            val eggs = store.all.any { it.remoteId == id("Eggs") && it.state == RecipeState.DELETED }
            assertTrue("Eggs deletion", eggs || "Eggs" !in names)
            val fig = store.all.any { it.local?.name == "Fig jam" && it.state == RecipeState.CREATED }
            assertTrue("Fig jam creation", fig || "Fig jam" in names)
            val gnocchi = store.all.any { it.local?.name == "Gnocchi mine" }
            assertTrue("Gnocchi edit", gnocchi)
        }

        override fun close() {
            web.close()
        }

        private fun JsonObject.name() = getValue("name").jsonPrimitive.content
    }

    @Test
    fun everyInterruptedRunKeepsAllChangesAndTheNextRunCatchesUp() = runTest {
        val (expected, requestCount) =
            World().use { world ->
                world.prepare()
                val before = world.server.log.size
                assertEquals(SyncResult.Success, world.engine.run())
                world.endState() to world.server.log.size - before
            }
        assertTrue("the full run sends many requests", requestCount > 10)

        for (position in 0 until requestCount) {
            for (answerLost in listOf(false, true)) {
                World().use { world ->
                    world.prepare()
                    val target = world.server.log.size + position
                    val matches = { _: mockwebserver3.RecordedRequest -> world.server.log.size - 1 == target }
                    if (answerLost) world.server.loseAnswer(matches) else world.server.drop(matches)

                    val interrupted = world.engine.run()
                    world.assertNothingLost()
                    world.server.heal()
                    val clean = world.engine.run()

                    val label = "request $position, answer lost: $answerLost, interrupted: $interrupted"
                    assertEquals(label, SyncResult.Success, clean)
                    assertEquals(label, expected, world.endState())
                }
            }
        }
    }
}
