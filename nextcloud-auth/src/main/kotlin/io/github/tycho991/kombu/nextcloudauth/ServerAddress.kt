package io.github.tycho991.kombu.nextcloudauth

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Why the text the user entered is not usable as a server address. */
public enum class AddressError {
    /** Nothing was entered. */
    EMPTY,

    /** The address uses plain `http://`; Kombu only supports HTTPS. */
    CLEARTEXT,

    /** The text is not a valid web address. */
    INVALID,
}

/**
 * The base address of a Nextcloud server, for example `https://cloud.example.com` or
 * `https://example.com/nextcloud`, without a trailing slash.
 */
public class ServerAddress private constructor(
    /** The base URL; its path is the Nextcloud installation directory. */
    public val url: HttpUrl,
) {
    /** Returns the URL of [path] (for example `status.php`) below the installation directory. */
    public fun resolve(path: String): HttpUrl = url.newBuilder().addPathSegments(path).build()

    /** Whether [other] has the same scheme, host and port as this address. */
    public fun hasSameOrigin(other: HttpUrl): Boolean =
        other.scheme == url.scheme && other.host == url.host && other.port == url.port

    override fun equals(other: Any?): Boolean = other is ServerAddress && other.url == url

    override fun hashCode(): Int = url.hashCode()

    override fun toString(): String = url.toString().removeSuffix("/")

    /** Ways to create a [ServerAddress]. */
    public companion object {
        private const val HTTPS_PREFIX = "https://"
        private const val HTTP_PREFIX = "http://"
        private const val SCHEME_SEPARATOR = "://"

        /**
         * Normalises what the user typed into a server address: adds `https://` when no scheme is given,
         * rejects `http://`, drops query and fragment, trailing slashes and a copied `/index.php/...`,
         * `/apps/...` or `/login` part, and keeps a sub-path of the installation.
         */
        public fun parse(input: String): ParsedAddress {
            val text = input.trim()
            val lower = text.lowercase()
            val rejection =
                when {
                    text.isEmpty() -> AddressError.EMPTY
                    lower.startsWith(HTTP_PREFIX) -> AddressError.CLEARTEXT
                    !lower.startsWith(HTTPS_PREFIX) && SCHEME_SEPARATOR in text -> AddressError.INVALID
                    else -> null
                }
            if (rejection != null) return ParsedAddress.Rejected(rejection)
            val url = (if (lower.startsWith(HTTPS_PREFIX)) text else HTTPS_PREFIX + text).toHttpUrlOrNull()
            return if (url == null || url.username.isNotEmpty() || url.password.isNotEmpty()) {
                ParsedAddress.Rejected(AddressError.INVALID)
            } else {
                ParsedAddress.Accepted(ServerAddress(normalise(url)))
            }
        }

        /** Wraps a URL that was already verified, for example one loaded from storage, without the input rules. */
        public fun of(url: HttpUrl): ServerAddress = ServerAddress(normalise(url))

        private fun normalise(url: HttpUrl): HttpUrl {
            val segments = url.pathSegments.filter { it.isNotEmpty() }
            val installation =
                when {
                    "index.php" in segments -> segments.subList(0, segments.indexOf("index.php"))
                    "apps" in segments -> segments.subList(0, segments.indexOf("apps"))
                    segments.lastOrNull() == "login" -> segments.dropLast(1)
                    else -> segments
                }
            val builder = url.newBuilder().query(null).fragment(null).encodedPath("/")
            installation.forEach { builder.addPathSegment(it) }
            return builder.build()
        }
    }
}

/** Result of [ServerAddress.parse]. */
public sealed interface ParsedAddress {
    /** The input is a usable [address]. */
    public data class Accepted(
        /** The normalised server address. */
        val address: ServerAddress,
    ) : ParsedAddress

    /** The input was rejected for [reason]. */
    public data class Rejected(
        /** Why the input is not usable. */
        val reason: AddressError,
    ) : ParsedAddress
}
