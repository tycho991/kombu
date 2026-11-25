package io.github.tycho991.kombu.cookbookclient

import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CookbookClientTest {
    private val server = MockWebServer()

    // Without retries, a dropped connection uses up exactly one queued answer.
    private val client = CookbookClient(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
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

    private fun enqueueBytes(bytes: ByteArray) {
        server.enqueue(
            MockResponse
                .Builder()
                .body(Buffer().write(bytes))
                .build(),
        )
    }

    private fun enqueueDisconnect() {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
    }

    /** One API call with its expected request and its answer for a normal success. */
    private class Endpoint(
        val name: String,
        val method: String,
        val path: String,
        val successBody: String,
        val expectedSuccess: Any?,
        val call: suspend () -> NextcloudResult<*>,
    )

    private val recipePath = "/nextcloud/index.php/apps/cookbook/api/v1/recipes"
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    private val endpoints: List<Endpoint>
        get() =
            listOf(
                Endpoint(
                    "list",
                    "GET",
                    recipePath,
                    """[{"id":"5678","name":"Kohl","dateModified":"2024-01-02T10:00:00+0100"}]""",
                    listOf(RecipeStub("5678", "Kohl", "2024-01-02T10:00:00+0100")),
                ) { client.listRecipes(credentials) },
                Endpoint("get", "GET", "$recipePath/5678", Fixtures.text("string-id"), Fixtures.stringId) {
                    client.getRecipe(credentials, "5678")
                },
                Endpoint("create", "POST", recipePath, "42", "42") {
                    client.createRecipe(credentials, Fixtures.numberId)
                },
                Endpoint("update", "PUT", "$recipePath/5678", "5678", Unit) {
                    client.updateRecipe(credentials, "5678", Fixtures.stringId)
                },
                Endpoint("delete", "DELETE", "$recipePath/5678", "\"Recipe 5678 deleted successfully\"", Unit) {
                    client.deleteRecipe(credentials, "5678")
                },
            )

    @Test
    fun `every endpoint sends its method, path and credentials and reads a success`() = runTest {
        endpoints.forEach { endpoint ->
            enqueue(200, endpoint.successBody)

            assertEquals(NextcloudResult.Success(endpoint.expectedSuccess), endpoint.call(), endpoint.name)
            val request = server.takeRequest()
            assertEquals(endpoint.method, request.method, endpoint.name)
            assertEquals(endpoint.path, request.url.encodedPath, endpoint.name)
            assertEquals(credentials.authorizationHeader(), request.headers["Authorization"], endpoint.name)
        }
    }

    @Test
    fun `every endpoint maps the error statuses`() = runTest {
        val expected =
            mapOf(
                401 to NextcloudError.Unauthorized,
                404 to NextcloudError.NotFound,
                406 to NextcloudError.ServerError(406),
                409 to NextcloudError.NameTaken,
                422 to NextcloudError.NameMissing,
                500 to NextcloudError.ServerError(500),
            )
        endpoints.forEach { endpoint ->
            expected.forEach { (code, error) ->
                enqueue(code, """{"msg":"error"}""")

                assertEquals(NextcloudResult.Failure(error), endpoint.call(), "${endpoint.name} $code")
                server.takeRequest()
            }
        }
    }

    @Test
    fun `every endpoint reports a lost connection as unreachable`() = runTest {
        endpoints.forEach { endpoint ->
            enqueueDisconnect()

            assertEquals(NextcloudResult.Failure(NextcloudError.Unreachable), endpoint.call(), endpoint.name)
            server.takeRequest()
        }
    }

    @Test
    fun `endpoints that read a body report an unreadable one as server error`() = runTest {
        endpoints.filter { it.name in setOf("list", "get", "create") }.forEach { endpoint ->
            enqueue(200, "<html>Dashboard</html>")

            assertEquals(NextcloudResult.Failure(NextcloudError.ServerError(200)), endpoint.call(), endpoint.name)
            server.takeRequest()
        }
        enqueue(200, "[]")
        assertEquals(NextcloudResult.Failure(NextcloudError.ServerError(200)), client.getRecipe(credentials, "1"))
        enqueue(200, "{}")
        assertEquals(NextcloudResult.Failure(NextcloudError.ServerError(200)), client.listRecipes(credentials))
    }

    @Test
    fun `endpoints without a result ignore the body`() = runTest {
        enqueue(200, "<html>ok</html>")
        assertEquals(NextcloudResult.Success(Unit), client.updateRecipe(credentials, "5678", Fixtures.stringId))
        enqueue(200)
        assertEquals(NextcloudResult.Success(Unit), client.deleteRecipe(credentials, "5678"))
    }

    @Test
    fun `create sends the recipe without id and reads a string id`() = runTest {
        enqueue(200, "\"43\"")

        assertEquals(NextcloudResult.Success("43"), client.createRecipe(credentials, Fixtures.numberId))

        val sent = checkNotNull(RemoteRecipe.parse(server.takeRequest().body!!.utf8()))
        assertNull(sent.id)
        assertEquals(Fixtures.numberId.without("id"), sent)
    }

    @Test
    fun `update sends every field and puts the id into the body`() = runTest {
        enqueue(200, "1234")
        val recipe = Fixtures.numberId.with("name", JsonPrimitive("Lemon Soup"))

        client.updateRecipe(credentials, "1234", recipe)
        val unchangedId = checkNotNull(RemoteRecipe.parse(server.takeRequest().body!!.utf8()))
        enqueue(200, "99")
        client.updateRecipe(credentials, "99", recipe)
        val newId = checkNotNull(RemoteRecipe.parse(server.takeRequest().body!!.utf8()))

        assertEquals(recipe, unchangedId)
        assertEquals(recipe.with("id", JsonPrimitive("99")), newId)
    }

    @Test
    fun `encodes the id as one path segment`() = runTest {
        enqueue(404)

        client.getRecipe(credentials, "a/b c")

        assertEquals("$recipePath/a%2Fb%20c", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `downloads an image as JPEG in the requested size`() = runTest {
        enqueueBytes(jpeg)

        val result = client.downloadImage(credentials, "5678", ImageSize.THUMB)

        assertArrayEquals(jpeg, (result as NextcloudResult.Success).value)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("$recipePath/5678/image", request.url.encodedPath)
        assertEquals("thumb", request.url.queryParameter("size"))
        assertEquals("image/jpeg", request.headers["Accept"])
        assertEquals(credentials.authorizationHeader(), request.headers["Authorization"])
    }

    @Test
    fun `reports no image for 406 and for a body that is not a JPEG`() = runTest {
        enqueue(406, """{"msg":"No image with the matching MIME type was found on the server."}""")
        assertEquals(NextcloudResult.Success(null), client.downloadImage(credentials, "5678", ImageSize.FULL))
        assertEquals("full", server.takeRequest().url.queryParameter("size"))

        enqueue(200, "<svg></svg>")
        assertEquals(NextcloudResult.Success(null), client.downloadImage(credentials, "5678", ImageSize.FULL))
        enqueue(200)
        assertEquals(NextcloudResult.Success(null), client.downloadImage(credentials, "5678", ImageSize.FULL))
    }

    @Test
    fun `image download maps errors`() = runTest {
        mapOf(
            401 to NextcloudError.Unauthorized,
            404 to NextcloudError.NotFound,
            500 to NextcloudError.ServerError(500),
        ).forEach { (code, error) ->
            enqueue(code)
            assertEquals(NextcloudResult.Failure(error), client.downloadImage(credentials, "5678", ImageSize.FULL))
        }
        enqueueDisconnect()
        assertEquals(
            NextcloudResult.Failure(NextcloudError.Unreachable),
            client.downloadImage(credentials, "5678", ImageSize.FULL),
        )
    }

    @Test
    fun `reads the version from the version route`() = runTest {
        enqueue(200, """{"cookbook_version":[0,11,12],"api_version":{"epoch":0,"major":1,"minor":2}}""")

        val result = client.checkAvailable(credentials)

        assertEquals(NextcloudResult.Success(CookbookVersion(listOf(0, 11, 12))), result)
        assertEquals("0.11.12", (result as NextcloudResult.Success).value.toString())
        val request = server.takeRequest()
        assertEquals("/nextcloud/index.php/apps/cookbook/api/version", request.url.encodedPath)
        assertEquals(credentials.authorizationHeader(), request.headers["Authorization"])
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `falls back to the capabilities when the version route is missing`() = runTest {
        enqueue(404, "<html>Not found</html>")
        enqueue(200, """{"ocs":{"meta":{"status":"ok"},"data":{"capabilities":{"cookbook":{"version":[0,11,12]}}}}}""")

        assertEquals(NextcloudResult.Success(CookbookVersion(listOf(0, 11, 12))), client.checkAvailable(credentials))

        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/nextcloud/ocs/v2.php/cloud/capabilities", request.url.encodedPath)
        assertEquals("json", request.url.queryParameter("format"))
        assertEquals("true", request.headers["OCS-APIRequest"])
        assertEquals(credentials.authorizationHeader(), request.headers["Authorization"])
    }

    @Test
    fun `reads a capabilities version given as text`() = runTest {
        enqueue(404)
        enqueue(200, """{"ocs":{"data":{"capabilities":{"cookbook":{"version":"0.12.0"}}}}}""")

        assertEquals(NextcloudResult.Success(CookbookVersion(listOf(0, 12, 0))), client.checkAvailable(credentials))
    }

    @Test
    fun `reports a missing app when the capabilities have no Cookbook version`() = runTest {
        val answers =
            listOf(
                """{"ocs":{"data":{"capabilities":{"theming":{}}}}}""",
                """{"ocs":{"data":{"capabilities":{"cookbook":{"version":"next"}}}}}""",
                """{"ocs":{"data":{"capabilities":{"cookbook":{"version":[0,"x"]}}}}}""",
                """{"ocs":{"data":{"capabilities":{"cookbook":{"version":[]}}}}}""",
                """{"ocs":{"data":[]}}""",
                "[]",
                "<html>",
            )
        answers.forEach { answer ->
            enqueue(404)
            enqueue(200, answer)

            assertEquals(
                NextcloudResult.Failure(NextcloudError.CookbookMissing),
                client.checkAvailable(credentials),
                answer,
            )
        }
    }

    @Test
    fun `passes on failures of the capabilities request`() = runTest {
        enqueue(404)
        enqueue(401)
        assertEquals(NextcloudResult.Failure(NextcloudError.Unauthorized), client.checkAvailable(credentials))

        enqueue(404)
        enqueue(503)
        assertEquals(NextcloudResult.Failure(NextcloudError.ServerError(503)), client.checkAvailable(credentials))
    }

    @Test
    fun `reports a missing app for a version answer without version`() = runTest {
        listOf("<html>Dashboard</html>", "{}", """{"cookbook_version":"not a list"}""").forEach { body ->
            enqueue(200, body)

            assertEquals(
                NextcloudResult.Failure(NextcloudError.CookbookMissing),
                client.checkAvailable(credentials),
                body,
            )
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `reports rejected credentials and server errors of the version route`() = runTest {
        enqueue(401)
        assertEquals(NextcloudResult.Failure(NextcloudError.Unauthorized), client.checkAvailable(credentials))

        enqueue(500)
        assertEquals(NextcloudResult.Failure(NextcloudError.ServerError(500)), client.checkAvailable(credentials))
    }

    @Test
    fun `supports Cookbook 0_10_3 and newer`() {
        val cases =
            mapOf(
                listOf(0, 10, 2) to false,
                listOf(0, 10, 3) to true,
                listOf(0, 10, 3, 1) to true,
                listOf(0, 11, 12) to true,
                listOf(1, 0) to true,
                listOf(0, 10) to false,
                listOf(0, 9, 20) to false,
                emptyList<Int>() to false,
            )
        cases.forEach { (parts, supported) ->
            assertEquals(supported, CookbookVersion(parts).isSupported, parts.toString())
        }
        assertEquals("0.10.3", CookbookVersion.MINIMUM.toString())
    }
}
