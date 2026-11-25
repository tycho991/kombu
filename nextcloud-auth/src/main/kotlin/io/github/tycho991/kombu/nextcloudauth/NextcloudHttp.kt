package io.github.tycho991.kombu.nextcloudauth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** The `User-Agent` Kombu sends; Nextcloud names the app password after it. */
public const val KOMBU_USER_AGENT: String = "Kombu"

/** Header that marks a request as an OCS API call. */
internal const val OCS_API_REQUEST_HEADER = "OCS-APIRequest"

internal val nextcloudJson: Json = Json { ignoreUnknownKeys = true }

/**
 * Executes [request] and turns the answer into a result with [read]. Connection failures become
 * [NextcloudError.Unreachable], TLS failures [NextcloudError.CertificateRejected]; no exception leaves this
 * function except cancellation.
 */
public suspend fun <T> OkHttpClient.fetch(
    request: Request,
    read: (Response) -> NextcloudResult<T>,
): NextcloudResult<T> = try {
    withContext(Dispatchers.IO) {
        newCall(request).executeAsync().use(read)
    }
} catch (e: IOException) {
    NextcloudResult.Failure(e.toNextcloudError())
}

internal fun IOException.toNextcloudError(): NextcloudError = when (this) {
    is SSLHandshakeException, is SSLPeerUnverifiedException -> NextcloudError.CertificateRejected
    else -> NextcloudError.Unreachable
}

/** Decodes the body as JSON into [T], or returns `null` when it is not valid JSON of that shape. */
internal inline fun <reified T> Response.decodeJsonOrNull(): T? = try {
    nextcloudJson.decodeFromString<T>(body.string())
} catch (ignored: SerializationException) {
    // Not JSON of the expected shape; callers treat that as "not the server we expected".
    null
} catch (ignored: IllegalArgumentException) {
    // Thrown by kotlinx.serialization for JSON that does not match the class; handled as above.
    null
}

/** Maps an answer without a body of interest: 2xx is success, 401 [NextcloudError.Unauthorized]. */
internal fun Response.toUnitResult(): NextcloudResult<Unit> = when {
    isSuccessful -> NextcloudResult.Success(Unit)
    code == HTTP_UNAUTHORIZED -> NextcloudResult.Failure(NextcloudError.Unauthorized)
    else -> NextcloudResult.Failure(NextcloudError.ServerError(code))
}

internal const val HTTP_UNAUTHORIZED = 401
internal const val HTTP_NOT_FOUND = 404
internal const val HTTP_SERVER_ERROR_MIN = 500
