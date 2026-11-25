package io.github.tycho991.kombu.nextcloudauth

import okhttp3.HttpUrl

/**
 * A running Login Flow v2: the verified [address], the [loginUrl] to open in a browser, and the
 * [pollEndpoint] and [token] to poll for the result. [startedAtMillis] is the wall-clock start, so that a
 * session restored after process death still expires on time. [toString] never reveals the token.
 */
public data class LoginSession(
    /** The server address that was verified before the login started. */
    val address: ServerAddress,
    /** The Nextcloud login page to open in a browser. */
    val loginUrl: HttpUrl,
    /** The endpoint to poll for the result. */
    val pollEndpoint: HttpUrl,
    /** The secret poll token. */
    val token: String,
    /** When the login started, in milliseconds since the epoch. */
    val startedAtMillis: Long,
) {
    override fun toString(): String =
        "LoginSession(address=$address, loginUrl=$loginUrl, pollEndpoint=$pollEndpoint, token=***, " +
            "startedAtMillis=$startedAtMillis)"
}

/** Result of one poll of a [LoginSession]. */
public sealed interface PollResult {
    /** The user has not approved the login yet. */
    public data object Pending : PollResult

    /** The user approved the login; Nextcloud created [credentials]. */
    public data class Approved(
        /** The new credentials, bound to the verified server address. */
        val credentials: NextcloudCredentials,
    ) : PollResult
}

/** What `status.php` reported about an installed, usable Nextcloud server. */
public data class ServerStatus(
    /** The Nextcloud version string, for example `31.0.2.1`. */
    val version: String,
)
