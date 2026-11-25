package io.github.tycho991.kombu.nextcloudauth

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ServerAddressTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "cloud.example.com, https://cloud.example.com",
        "'  cloud.example.com  ', https://cloud.example.com",
        "https://cloud.example.com, https://cloud.example.com",
        "HTTPS://Cloud.Example.com/, https://cloud.example.com",
        "https://cloud.example.com///, https://cloud.example.com",
        "https://example.com/nextcloud/, https://example.com/nextcloud",
        "example.com/nextcloud, https://example.com/nextcloud",
        "https://cloud.example.com/index.php, https://cloud.example.com",
        "https://cloud.example.com/index.php/login, https://cloud.example.com",
        "https://example.com/nextcloud/index.php/apps/cookbook/, https://example.com/nextcloud",
        "https://cloud.example.com/apps/files/, https://cloud.example.com",
        "https://cloud.example.com/login, https://cloud.example.com",
        "https://cloud.example.com/?redirect=1#top, https://cloud.example.com",
        "https://cloud.example.com:8443, https://cloud.example.com:8443",
    )
    fun `normalises accepted input`(input: String, expected: String) {
        assertEquals(ParsedAddress.Accepted(ServerAddress.of(expected.toHttpUrl())), ServerAddress.parse(input))
        assertEquals(expected, (ServerAddress.parse(input) as ParsedAddress.Accepted).address.toString())
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "'', EMPTY",
        "'   ', EMPTY",
        "http://cloud.example.com, CLEARTEXT",
        "HTTP://cloud.example.com, CLEARTEXT",
        "ftp://cloud.example.com, INVALID",
        "cloud example com, INVALID",
        "https://, INVALID",
        "https://user:secret@cloud.example.com, INVALID",
        "https://user@cloud.example.com, INVALID",
        "https://:secret@cloud.example.com, INVALID",
    )
    fun `rejects unusable input`(input: String, expected: AddressError) {
        assertEquals(ParsedAddress.Rejected(expected), ServerAddress.parse(input))
    }

    @Test
    fun `resolves paths below the installation directory`() {
        val root = ServerAddress.of("https://cloud.example.com".toHttpUrl())
        val subPath = ServerAddress.of("https://example.com/nextcloud/".toHttpUrl())

        assertEquals("https://cloud.example.com/status.php", root.resolve("status.php").toString())
        assertEquals(
            "https://example.com/nextcloud/index.php/login/v2",
            subPath.resolve("index.php/login/v2").toString(),
        )
    }

    @Test
    fun `compares origins by scheme host and port`() {
        val address = ServerAddress.of("https://cloud.example.com/nextcloud".toHttpUrl())

        assertTrue(address.hasSameOrigin("https://cloud.example.com/other/path".toHttpUrl()))
        assertFalse(address.hasSameOrigin("http://cloud.example.com/".toHttpUrl()))
        assertFalse(address.hasSameOrigin("https://attacker.example.org/".toHttpUrl()))
        assertFalse(address.hasSameOrigin("https://cloud.example.com:8443/".toHttpUrl()))
    }

    @Test
    fun `implements equality by url`() {
        val first = ServerAddress.of("https://cloud.example.com/".toHttpUrl())
        val second = ServerAddress.of("https://cloud.example.com".toHttpUrl())
        val other = ServerAddress.of("https://other.example.com".toHttpUrl())

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, other)
        assertFalse(first.equals("https://cloud.example.com"))
    }
}
