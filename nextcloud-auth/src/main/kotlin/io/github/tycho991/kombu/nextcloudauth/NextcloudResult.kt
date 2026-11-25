package io.github.tycho991.kombu.nextcloudauth

/** Outcome of a call to a Nextcloud server: a value or one of the [NextcloudError] kinds, never an exception. */
public sealed interface NextcloudResult<out T> {
    /** The call succeeded with [value]. */
    public data class Success<out T>(
        /** The result of the call. */
        val value: T,
    ) : NextcloudResult<T>

    /** The call failed for the reason in [error]. */
    public data class Failure(
        /** Why the call failed. */
        val error: NextcloudError,
    ) : NextcloudResult<Nothing>
}

/** The closed set of reasons why talking to a Nextcloud server can fail; the app explains each to the user. */
public sealed interface NextcloudError {
    /** The address the user entered was rejected before any request, see [reason]. */
    public data class InvalidAddress(
        /** Why the address was rejected. */
        val reason: AddressError,
    ) : NextcloudError

    /** The server could not be reached: no network, unknown host, refused connection or timeout. */
    public data object Unreachable : NextcloudError

    /** The TLS handshake failed, for example because the device does not trust the certificate. */
    public data object CertificateRejected : NextcloudError

    /** The address answered, but not as an installed Nextcloud, or the login pointed to another host. */
    public data object NotNextcloud : NextcloudError

    /** The server is in maintenance mode or waits for a database upgrade. */
    public data object Maintenance : NextcloudError

    /** The login was not approved within the lifetime of the login token. */
    public data object LoginExpired : NextcloudError

    /** The server rejected the credentials. */
    public data object Unauthorized : NextcloudError

    /** The Cookbook app is not installed or not enabled for the account. */
    public data object CookbookMissing : NextcloudError

    /** The requested object, for example a recipe, does not exist on the server (HTTP 404). */
    public data object NotFound : NextcloudError

    /** The server refused to save a recipe because another recipe already has its name (HTTP 409). */
    public data object NameTaken : NextcloudError

    /** The server refused to save a recipe because it has no name (HTTP 422). */
    public data object NameMissing : NextcloudError

    /** The server answered with an unexpected HTTP status or an unreadable body. */
    public data class ServerError(
        /** The HTTP status code of the answer. */
        val statusCode: Int,
    ) : NextcloudError
}
