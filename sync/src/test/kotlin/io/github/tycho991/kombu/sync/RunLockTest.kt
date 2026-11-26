package io.github.tycho991.kombu.sync

import io.github.tycho991.kombu.testcookbookserver.FakeCookbookServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Task 5.5: runs never overlap, and a change made during a run is uploaded by one more push (design D5, D10). */
class RunLockTest : SyncTestBase() {
    @Test
    fun concurrentRunsNeverOverlap() = runBlocking {
        server.add(FakeCookbookServer.recipe("Soup"))
        // A slow version answer leaves time for a second run to start, if the lock let it.
        server.fail(
            MockResponse
                .Builder()
                .headersDelay(SLOW_MILLIS, TimeUnit.MILLISECONDS)
                .body("""{"cookbook_version":[0,11,12]}""")
                .build(),
        ) { it.url.encodedPath.endsWith("/api/version") }

        val results = List(2) { async(Dispatchers.IO) { engine.run() } }.awaitAll()

        assertEquals(listOf(SyncResult.Success, SyncResult.Success), results)
        val log = server.log
        assertEquals(VERSION, log[0])
        assertEquals(LIST, log[1])
        val second = log.indexOf(VERSION).let { first -> log.subList(first + 1, log.size).indexOf(VERSION) + first + 1 }
        assertEquals("the second run starts after the first one ended", log.size - 2, second)
        assertEquals(LIST, log.last())
    }

    @Test
    fun aChangeDuringARunIsUploadedByExactlyOneMorePush() = runTest {
        val soup = server.add(FakeCookbookServer.recipe("Soup"))
        sync()
        val localId = store.byRemoteId(soup)!!.localId
        val bread = server.add(
            FakeCookbookServer.recipe("Bread", image = "https://example.org/bread.jpg"),
            image = true,
        )
        // The user edits Soup while the run downloads images, after its first push has already looked for changes.
        var edited = false
        store.beforeWrite = {
            if (!edited && server.log.last().startsWith(image(bread))) {
                edited = true
                store.edit(localId) { it.with("description", JsonPrimitive("mine")) }
            }
        }

        val (result, requests) = sync()
        store.beforeWrite = {}

        assertEquals(SyncResult.Success, result)
        assertEquals(listOf(put(soup)), requests.filter { it.startsWith("PUT") })
        assertEquals(requests.indexOfLast { it.startsWith(image(bread)) } + 2, requests.indexOf(put(soup)))
        assertEquals(JsonPrimitive("mine"), server.stored.getValue(soup)["description"])
    }

    @Test
    fun aRunWithoutLocalChangesPushesOnlyOnce() = runTest {
        val id = server.add(FakeCookbookServer.recipe("Soup"))
        sync()
        store.edit(store.byRemoteId(id)!!.localId) { it.renamed("My soup") }

        val (_, requests) = sync()

        assertEquals(listOf(VERSION, LIST, get(id), put(id), get(id)), requests)
    }

    private companion object {
        const val SLOW_MILLIS = 300L
    }
}
