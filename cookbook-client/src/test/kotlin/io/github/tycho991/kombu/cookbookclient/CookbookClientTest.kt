package io.github.tycho991.kombu.cookbookclient

import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CookbookClientTest {
    private val server = MockWebServer()
    private val client = CookbookClient(OkHttpClient())
    private lateinit var credentials: NextcloudCredentials

    @BeforeEach
    fun startServer() {
        server.start()
        credentials = NextcloudCredentials(ServerAddress.of(server.url("/nextcloud")), "alex", "app-password")
    }

    @AfterEach
    fun stopServer() {
        server.close()
    }

    private fun enqueue(code: Int, body: String = "") {
        server.enqueue(
            MockResponse
                .Builder()
                .code(code)
                .body(body)
                .build(),
        )
    }

    @Test
    fun `reads the version of an available Cookbook app`() = runTest {
        enqueue(200, """{"cookbook_version":[0,11,2],"api_version":{"epoch":0,"major":1,"minor":2}}""")

        val result = client.checkAvailable(credentials)

        assertEquals(NextcloudResult.Success(CookbookVersion(listOf(0, 11, 2))), result)
        assertEquals("0.11.2", (result as NextcloudResult.Success).value.toString())
        val request = server.takeRequest()
        assertEquals("/nextcloud/index.php/apps/cookbook/api/v1/version", request.url.encodedPath)
        assertEquals(credentials.authorizationHeader(), request.headers["Authorization"])
    }

    @Test
    fun `accepts an answer without version`() = runTest {
        enqueue(200, "{}")

        assertEquals(NextcloudResult.Success(CookbookVersion(emptyList())), client.checkAvailable(credentials))
    }

    @Test
    fun `reports a missing app`() = runTest {
        enqueue(404, "<html>Not found</html>")

        assertEquals(NextcloudResult.Failure(NextcloudError.CookbookMissing), client.checkAvailable(credentials))
    }

    @Test
    fun `reports an HTML page as missing app`() = runTest {
        enqueue(200, "<html>Dashboard</html>")

        assertEquals(NextcloudResult.Failure(NextcloudError.CookbookMissing), client.checkAvailable(credentials))
    }

    @Test
    fun `reports JSON of another shape as missing app`() = runTest {
        enqueue(200, """{"cookbook_version":"not a list"}""")

        assertEquals(NextcloudResult.Failure(NextcloudError.CookbookMissing), client.checkAvailable(credentials))
    }

    @Test
    fun `reports rejected credentials`() = runTest {
        enqueue(401)

        assertEquals(NextcloudResult.Failure(NextcloudError.Unauthorized), client.checkAvailable(credentials))
    }

    @Test
    fun `reports a server error`() = runTest {
        enqueue(500)

        assertEquals(NextcloudResult.Failure(NextcloudError.ServerError(500)), client.checkAvailable(credentials))
    }
}
