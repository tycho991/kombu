package io.github.tycho991.kombu.cookbookclient

import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import io.github.tycho991.kombu.nextcloudauth.fetch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Client for the Nextcloud Cookbook REST API of one account.
 *
 * @param httpClient the HTTP client to use; the caller configures timeouts and the user agent.
 */
public class CookbookClient(private val httpClient: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Checks that the Cookbook app answers for the account in [credentials] and returns the version it reports.
     * A missing app gives [NextcloudError.CookbookMissing], rejected credentials [NextcloudError.Unauthorized].
     * No minimum version is enforced yet (open question O9).
     */
    public suspend fun checkAvailable(credentials: NextcloudCredentials): NextcloudResult<CookbookVersion> {
        val request =
            Request
                .Builder()
                .url(credentials.server.resolve("index.php/apps/cookbook/api/v1/version"))
                .header("Authorization", credentials.authorizationHeader())
                .header("Accept", "application/json")
                .get()
                .build()
        return httpClient.fetch(request, ::readVersion)
    }

    private fun readVersion(response: Response): NextcloudResult<CookbookVersion> = when {
        response.code == HTTP_UNAUTHORIZED -> NextcloudResult.Failure(NextcloudError.Unauthorized)

        response.isSuccessful ->
            decodeVersion(response.body.string())
                ?.let { NextcloudResult.Success(it) }
                ?: NextcloudResult.Failure(NextcloudError.CookbookMissing)

        response.code == HTTP_NOT_FOUND -> NextcloudResult.Failure(NextcloudError.CookbookMissing)

        else -> NextcloudResult.Failure(NextcloudError.ServerError(response.code))
    }

    private fun decodeVersion(body: String): CookbookVersion? = try {
        CookbookVersion(json.decodeFromString<VersionAnswer>(body).cookbookVersion)
    } catch (ignored: SerializationException) {
        // An HTML page instead of JSON: Nextcloud answered, but not the Cookbook app.
        null
    } catch (ignored: IllegalArgumentException) {
        // JSON of another shape; treated like the HTML page above.
        null
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}

/** The version the Cookbook app reports, as its numeric [parts] (for example `[0, 11, 0]`). */
public data class CookbookVersion(
    /** The numeric version parts; empty when the app did not report a version. */
    val parts: List<Int>,
) {
    override fun toString(): String = parts.joinToString(".")
}

@Serializable
internal data class VersionAnswer(
    @SerialName("cookbook_version")
    val cookbookVersion: List<Int> = emptyList(),
)
