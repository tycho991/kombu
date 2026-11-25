package io.github.tycho991.kombu.nextcloudauth

import okhttp3.Credentials

/**
 * Credentials for one Nextcloud account: the verified [server], the [loginName] and an [appPassword] created
 * by Login Flow v2. [toString] never reveals the app password.
 */
public data class NextcloudCredentials(
    /** The server the account belongs to. */
    val server: ServerAddress,
    /** The login name Nextcloud reported for the account. */
    val loginName: String,
    /** The app password; it is a secret and must never be logged. */
    val appPassword: String,
) {
    /** The value of an HTTP `Authorization` header for these credentials (Basic auth, UTF-8). */
    public fun authorizationHeader(): String = Credentials.basic(loginName, appPassword, Charsets.UTF_8)

    override fun toString(): String = "NextcloudCredentials(server=$server, loginName=$loginName, appPassword=***)"
}
