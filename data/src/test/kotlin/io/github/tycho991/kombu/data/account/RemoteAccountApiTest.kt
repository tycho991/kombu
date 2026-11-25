package io.github.tycho991.kombu.data.account

import io.github.tycho991.kombu.cookbookclient.CookbookClient
import io.github.tycho991.kombu.cookbookclient.CookbookVersion
import io.github.tycho991.kombu.nextcloudauth.NextcloudAuthClient
import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import io.github.tycho991.kombu.nextcloudauth.ServerStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAccountApiTest {
    @Test
    fun delegatesToTheNextcloudAndCookbookClients() = runTest {
        MockWebServer().use { server ->
            server.start()
            val address = ServerAddress.of(server.url("/"))
            val credentials = NextcloudCredentials(address, "alex", "app-password")
            val http = OkHttpClient()
            val api = RemoteAccountApi(NextcloudAuthClient(http), CookbookClient(http))
            val poll = server.url("/login/v2/poll")
            val login = server.url("/login/v2/flow")
            listOf(
                """{"installed":true,"version":"31.0.0"}""",
                """{"poll":{"token":"t","endpoint":"$poll"},"login":"$login"}""",
                """{"server":"x","loginName":"alex","appPassword":"app-password"}""",
                "{}",
                """{"cookbook_version":[0,11]}""",
                "{}",
            ).forEach { server.enqueue(MockResponse.Builder().body(it).build()) }

            assertEquals(NextcloudResult.Success(ServerStatus("31.0.0")), api.checkServer(address))
            val session = (api.startLogin(address) as NextcloudResult.Success).value
            assertEquals(NextcloudResult.Success(credentials), api.awaitApproval(session, Channel()))
            assertEquals(NextcloudResult.Success(Unit), api.verifyAccount(credentials))
            assertEquals(NextcloudResult.Success(CookbookVersion(listOf(0, 11))), api.checkCookbook(credentials))
            assertEquals(NextcloudResult.Success(Unit), api.revokeAppPassword(credentials))
            assertTrue(server.requestCount == 6)
        }
    }
}
