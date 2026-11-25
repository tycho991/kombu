package io.github.tycho991.kombu.nextcloudauth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class NextcloudAuthClientTest {
    private val server = MockWebServer()
    private var now = 1_000_000L
    private val client = NextcloudAuthClient(OkHttpClient(), clock = { now })
    private lateinit var address: ServerAddress
    private val credentials get() = NextcloudCredentials(address, "alex", "app-password")

    @BeforeEach
    fun startServer() {
        server.start()
        address = ServerAddress.of(server.url("/nextcloud/"))
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

    private fun startAnswer(
        login: String = server.url("/nextcloud/login/v2/flow/abc").toString(),
        endpoint: String = server.url("/nextcloud/login/v2/poll").toString(),
    ) = """{"poll":{"token":"poll-token","endpoint":"$endpoint"},"login":"$login"}"""

    private fun session() = LoginSession(
        address = address,
        loginUrl = server.url("/nextcloud/login/v2/flow/abc"),
        pollEndpoint = server.url("/nextcloud/login/v2/poll"),
        token = "poll-token",
        startedAtMillis = now,
    )

    @Nested
    inner class ServerCheck {
        @Test
        fun `accepts an installed server and reads its version`() = runTest {
            enqueue(200, """{"installed":true,"maintenance":false,"needsDbUpgrade":false,"version":"31.0.2.1"}""")

            assertEquals(NextcloudResult.Success(ServerStatus("31.0.2.1")), client.checkServer(address))
            assertEquals("/nextcloud/status.php", server.takeRequest().url.encodedPath)
        }

        @Test
        fun `reports maintenance mode`() = runTest {
            enqueue(200, """{"installed":true,"maintenance":true}""")

            assertEquals(NextcloudResult.Failure(NextcloudError.Maintenance), client.checkServer(address))
        }

        @Test
        fun `reports a pending database upgrade as maintenance`() = runTest {
            enqueue(503, """{"installed":true,"needsDbUpgrade":true}""")

            assertEquals(NextcloudResult.Failure(NextcloudError.Maintenance), client.checkServer(address))
        }

        @Test
        fun `rejects an answer that is not JSON`() = runTest {
            enqueue(200, "<html>Welcome</html>")

            assertEquals(NextcloudResult.Failure(NextcloudError.NotNextcloud), client.checkServer(address))
        }

        @Test
        fun `rejects JSON without installed flag`() = runTest {
            enqueue(200, """{"status":"ok"}""")

            assertEquals(NextcloudResult.Failure(NextcloudError.NotNextcloud), client.checkServer(address))
        }

        @Test
        fun `rejects a server that reports it is not installed`() = runTest {
            enqueue(200, """{"installed":false,"maintenance":false}""")

            assertEquals(NextcloudResult.Failure(NextcloudError.NotNextcloud), client.checkServer(address))
        }

        @Test
        fun `reports an unusable error page as server error`() = runTest {
            enqueue(503, """{"installed":false}""")

            assertEquals(NextcloudResult.Failure(NextcloudError.ServerError(503)), client.checkServer(address))
        }

        @Test
        fun `rejects a missing status page`() = runTest {
            enqueue(404, "Not found")

            assertEquals(NextcloudResult.Failure(NextcloudError.NotNextcloud), client.checkServer(address))
        }

        @Test
        fun `reports a server error`() = runTest {
            enqueue(500, "Internal error")

            assertEquals(NextcloudResult.Failure(NextcloudError.ServerError(500)), client.checkServer(address))
        }
    }

    @Nested
    inner class LoginStart {
        @Test
        fun `starts the flow with the Kombu user agent`() = runTest {
            enqueue(200, startAnswer())

            val result = client.startLogin(address)

            val expected =
                LoginSession(
                    address = address,
                    loginUrl = server.url("/nextcloud/login/v2/flow/abc"),
                    pollEndpoint = server.url("/nextcloud/login/v2/poll"),
                    token = "poll-token",
                    startedAtMillis = now,
                )
            assertEquals(NextcloudResult.Success(expected), result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/nextcloud/index.php/login/v2", request.url.encodedPath)
            assertEquals(KOMBU_USER_AGENT, request.headers["User-Agent"])
        }

        @Test
        fun `aborts when the poll endpoint is on another host`() = runTest {
            enqueue(200, startAnswer(endpoint = "https://attacker.example.org/login/v2/poll"))

            assertEquals(NextcloudResult.Failure(NextcloudError.NotNextcloud), client.startLogin(address))
        }

        @Test
        fun `aborts when the login page uses another scheme`() = runTest {
            val login = server.url("/nextcloud/login/v2/flow/abc").toString().replace("http://", "https://")
            enqueue(200, startAnswer(login = login))

            assertEquals(NextcloudResult.Failure(NextcloudError.NotNextcloud), client.startLogin(address))
        }

        @Test
        fun `aborts on an unreadable answer`() = runTest {
            enqueue(200, """{"login":"no poll data"}""")

            assertEquals(NextcloudResult.Failure(NextcloudError.NotNextcloud), client.startLogin(address))
        }

        @Test
        fun `reports a missing login endpoint as not Nextcloud`() = runTest {
            enqueue(404)

            assertEquals(NextcloudResult.Failure(NextcloudError.NotNextcloud), client.startLogin(address))
        }

        @Test
        fun `reports a server error`() = runTest {
            enqueue(502)

            assertEquals(NextcloudResult.Failure(NextcloudError.ServerError(502)), client.startLogin(address))
        }
    }

    @Nested
    inner class Polling {
        @Test
        fun `reports pending until approved`() = runTest {
            enqueue(404)
            val result = client.poll(session())

            assertEquals(NextcloudResult.Success(PollResult.Pending), result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("token=poll-token", request.body?.utf8())
        }

        @Test
        fun `binds approved credentials to the verified address`() = runTest {
            enqueue(200, """{"server":"http://internal-host","loginName":"alex","appPassword":"app-password"}""")

            val result = client.poll(session())

            assertEquals(NextcloudResult.Success(PollResult.Approved(credentials)), result)
        }

        @Test
        fun `expires after the login lifetime without a request`() = runTest {
            val session = session()
            now += NextcloudAuthClient.LOGIN_LIFETIME.inWholeMilliseconds

            assertEquals(NextcloudResult.Failure(NextcloudError.LoginExpired), client.poll(session))
            assertEquals(0, server.requestCount)
        }

        @Test
        fun `reports an unreadable approval as server error`() = runTest {
            enqueue(200, "not json")

            assertEquals(NextcloudResult.Failure(NextcloudError.ServerError(200)), client.poll(session()))
        }

        @Test
        fun `waits through pending polls until approval`() = runTest {
            enqueue(404)
            enqueue(404)
            enqueue(200, """{"server":"x","loginName":"alex","appPassword":"app-password"}""")

            val result = client.awaitApproval(session(), Channel(), interval = 2.seconds)

            assertEquals(NextcloudResult.Success(credentials), result)
            assertEquals(3, server.requestCount)
        }

        // The virtual clock shows that the wake-up, not the one-hour interval, triggered the second poll.
        @OptIn(ExperimentalCoroutinesApi::class)
        @Test
        fun `polls at once when woken up`() = runTest {
            enqueue(404)
            enqueue(200, """{"server":"x","loginName":"alex","appPassword":"app-password"}""")
            val wakeUps = Channel<Unit>(Channel.CONFLATED)

            val waiting = async { client.awaitApproval(session(), wakeUps, interval = 1.hours) }
            wakeUps.send(Unit)

            assertEquals(NextcloudResult.Success(credentials), waiting.await())
            assertTrue(testScheduler.currentTime < 1.hours.inWholeMilliseconds)
        }

        @Test
        fun `stops waiting when the login expires`() = runTest {
            val session = session()
            now += NextcloudAuthClient.LOGIN_LIFETIME.inWholeMilliseconds

            assertEquals(
                NextcloudResult.Failure(NextcloudError.LoginExpired),
                client.awaitApproval(session, Channel()),
            )
        }

        @Test
        fun `stops waiting on a server error`() = runTest {
            enqueue(500)

            assertEquals(
                NextcloudResult.Failure(NextcloudError.ServerError(500)),
                client.awaitApproval(session(), Channel()),
            )
        }

        @Test
        fun `keeps waiting while the server is briefly unreachable`() = runTest {
            val calls = AtomicInteger()
            val flaky =
                OkHttpClient
                    .Builder()
                    .addInterceptor(
                        Interceptor { chain ->
                            if (calls.getAndIncrement() == 0) throw IOException("offline")
                            chain.proceed(chain.request())
                        },
                    ).build()
            enqueue(200, """{"server":"x","loginName":"alex","appPassword":"app-password"}""")

            val result = NextcloudAuthClient(flaky, clock = { now }).awaitApproval(session(), Channel())

            assertEquals(NextcloudResult.Success(credentials), result)
            assertEquals(2, calls.get())
        }
    }

    @Nested
    inner class AccountRequests {
        @Test
        fun `verifies the account with an authenticated OCS request`() = runTest {
            enqueue(200, """{"ocs":{"data":{"id":"alex"}}}""")

            assertEquals(NextcloudResult.Success(Unit), client.verifyAccount(credentials))
            val request = server.takeRequest()
            assertEquals("/nextcloud/ocs/v2.php/cloud/user", request.url.encodedPath)
            assertEquals("json", request.url.queryParameter("format"))
            assertEquals("true", request.headers["OCS-APIRequest"])
            assertEquals(credentials.authorizationHeader(), request.headers["Authorization"])
        }

        @Test
        fun `reports rejected credentials`() = runTest {
            enqueue(401)

            assertEquals(NextcloudResult.Failure(NextcloudError.Unauthorized), client.verifyAccount(credentials))
        }

        @Test
        fun `revokes the app password`() = runTest {
            enqueue(200, """{"ocs":{"meta":{"status":"ok"}}}""")

            assertEquals(NextcloudResult.Success(Unit), client.revokeAppPassword(credentials))
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/nextcloud/ocs/v2.php/core/apppassword", request.url.encodedPath)
            assertEquals("true", request.headers["OCS-APIRequest"])
            assertEquals(credentials.authorizationHeader(), request.headers["Authorization"])
        }

        @Test
        fun `reports a failed revocation`() = runTest {
            enqueue(403)

            assertEquals(
                NextcloudResult.Failure(NextcloudError.ServerError(403)),
                client.revokeAppPassword(credentials),
            )
        }
    }
}
