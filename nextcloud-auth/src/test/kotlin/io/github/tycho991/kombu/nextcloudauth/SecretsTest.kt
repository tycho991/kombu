package io.github.tycho991.kombu.nextcloudauth

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class SecretsTest {
    private val server = ServerAddress.of("https://cloud.example.com".toHttpUrl())

    @Test
    fun `credentials never print the app password`() {
        val text = NextcloudCredentials(server, "alex", "secret-app-password").toString()

        assertFalse("secret-app-password" in text)
        assertTrue("alex" in text)
    }

    @Test
    fun `credentials build a basic authorization header in UTF-8`() {
        val header = NextcloudCredentials(server, "jürgen", "pässword").authorizationHeader()

        val decoded = String(Base64.getDecoder().decode(header.removePrefix("Basic ")), Charsets.UTF_8)
        assertEquals("jürgen:pässword", decoded)
    }

    @Test
    fun `login session never prints the poll token`() {
        val session =
            LoginSession(
                address = server,
                loginUrl = "https://cloud.example.com/login/v2/flow/abc".toHttpUrl(),
                pollEndpoint = "https://cloud.example.com/login/v2/poll".toHttpUrl(),
                token = "secret-poll-token",
                startedAtMillis = 0,
            )

        assertFalse("secret-poll-token" in session.toString())
    }
}
