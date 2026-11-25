package io.github.tycho991.kombu.nextcloudauth

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.ServerSocket

class ConnectionErrorsTest {
    @Test
    fun `a closed port is unreachable`() = runTest {
        val port = ServerSocket(0).use { it.localPort }
        val address = ServerAddress.of(
            okhttp3.HttpUrl.Builder().scheme("http").host("127.0.0.1").port(port).build(),
        )

        val result = NextcloudAuthClient(OkHttpClient()).checkServer(address)

        assertEquals(NextcloudResult.Failure(NextcloudError.Unreachable), result)
    }

    @Test
    fun `an untrusted certificate is rejected`() = runTest {
        val certificate = heldCertificate("localhost")
        MockWebServer().use { server ->
            server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory())
            server.enqueue(MockResponse.Builder().body("""{"installed":true}""").build())
            server.start()

            val result = NextcloudAuthClient(OkHttpClient()).checkServer(ServerAddress.of(server.url("/")))

            assertEquals(NextcloudResult.Failure(NextcloudError.CertificateRejected), result)
        }
    }

    @Test
    fun `a certificate for another host is rejected`() = runTest {
        val certificate = heldCertificate("other.example.org")
        MockWebServer().use { server ->
            server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory())
            server.enqueue(MockResponse.Builder().body("""{"installed":true}""").build())
            server.start()
            val trusting = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
            val client =
                OkHttpClient
                    .Builder()
                    .sslSocketFactory(trusting.sslSocketFactory(), trusting.trustManager)
                    .build()

            val result = NextcloudAuthClient(client).checkServer(ServerAddress.of(server.url("/")))

            assertEquals(NextcloudResult.Failure(NextcloudError.CertificateRejected), result)
        }
    }

    private fun heldCertificate(host: String): HeldCertificate = HeldCertificate
        .Builder()
        .commonName(host)
        .addSubjectAlternativeName(host)
        .build()
}
