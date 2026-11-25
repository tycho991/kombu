package io.github.tycho991.kombu.nextcloudauth

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

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
        val certificate = heldCertificate(LOOPBACK)
        MockWebServer().use { server ->
            server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory())
            server.enqueue(MockResponse.Builder().body("""{"installed":true}""").build())
            server.start(InetAddress.getByName(LOOPBACK), 0)

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
            server.start(InetAddress.getByName(LOOPBACK), 0)
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

    @Test
    fun `a certificate error behind another route failure is still a certificate error`() {
        val refused = ConnectException("IPv6 route refused")
        refused.addSuppressed(SSLHandshakeException("IPv4 route: untrusted certificate"))

        assertEquals(NextcloudError.CertificateRejected, refused.toNextcloudError())
    }

    @Test
    fun `a certificate error as cause is a certificate error`() {
        val wrapped = IOException("handshake failed", SSLPeerUnverifiedException("hostname mismatch"))

        assertEquals(NextcloudError.CertificateRejected, wrapped.toNextcloudError())
    }

    @Test
    fun `other connection failures are unreachable`() {
        val failure = SocketTimeoutException("timeout")
        failure.addSuppressed(ConnectException("refused"))

        assertEquals(NextcloudError.Unreachable, failure.toNextcloudError())
    }

    private fun heldCertificate(host: String): HeldCertificate = HeldCertificate
        .Builder()
        .commonName(host)
        .addSubjectAlternativeName(host)
        .build()

    private companion object {
        // A fixed IPv4 address, so that the client has a single route and the handshake is the only failure.
        const val LOOPBACK = "127.0.0.1"
    }
}
