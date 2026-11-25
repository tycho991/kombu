package io.github.tycho991.kombu.nextcloudauth

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Signs in to a Nextcloud server with Login Flow v2 and manages the resulting app password.
 *
 * @param httpClient the HTTP client to use; the caller configures timeouts.
 * @param clock the current wall-clock time in milliseconds, replaceable in tests.
 */
public class NextcloudAuthClient(
    private val httpClient: OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Checks through `status.php` that [address] is an installed Nextcloud that is not in maintenance. */
    public suspend fun checkServer(address: ServerAddress): NextcloudResult<ServerStatus> {
        val request = Request.Builder().url(address.resolve("status.php")).get().build()
        return httpClient.fetch(request, ::readStatus)
    }

    /**
     * Starts Login Flow v2 at [address]. The login URL and the poll endpoint must have the same origin as
     * [address]; otherwise the poll token would go to another host, and the result is
     * [NextcloudError.NotNextcloud].
     */
    public suspend fun startLogin(address: ServerAddress): NextcloudResult<LoginSession> {
        val request =
            Request
                .Builder()
                .url(address.resolve("index.php/login/v2"))
                .header("User-Agent", KOMBU_USER_AGENT)
                .post(FormBody.Builder().build())
                .build()
        return httpClient.fetch(request) { response -> readLoginStart(address, response) }
    }

    /** Polls [session] once. After [LOGIN_LIFETIME] it returns [NextcloudError.LoginExpired] without a request. */
    public suspend fun poll(session: LoginSession): NextcloudResult<PollResult> {
        if (clock() - session.startedAtMillis >= LOGIN_LIFETIME.inWholeMilliseconds) {
            return NextcloudResult.Failure(NextcloudError.LoginExpired)
        }
        val request =
            Request
                .Builder()
                .url(session.pollEndpoint)
                .post(FormBody.Builder().add("token", session.token).build())
                .build()
        return httpClient.fetch(request) { response -> readPoll(session, response) }
    }

    /**
     * Polls [session] every [interval], and at once whenever [wakeUps] delivers an element, until the login is
     * approved or fails. A temporarily unreachable server does not end the wait; the login lifetime does.
     */
    public suspend fun awaitApproval(
        session: LoginSession,
        wakeUps: ReceiveChannel<Unit>,
        interval: Duration = POLL_INTERVAL,
    ): NextcloudResult<NextcloudCredentials> {
        while (true) {
            when (val result = poll(session)) {
                is NextcloudResult.Success -> {
                    val state = result.value
                    if (state is PollResult.Approved) return NextcloudResult.Success(state.credentials)
                }

                is NextcloudResult.Failure -> {
                    if (result.error != NextcloudError.Unreachable) return result
                }
            }
            withTimeoutOrNull(interval) { wakeUps.receiveCatching() }
        }
    }

    /** Checks with an authenticated OCS request that [credentials] are accepted by the server. */
    public suspend fun verifyAccount(credentials: NextcloudCredentials): NextcloudResult<Unit> {
        val url =
            credentials.server
                .resolve("ocs/v2.php/cloud/user")
                .newBuilder()
                .addQueryParameter("format", "json")
                .build()
        return httpClient.fetch(ocsRequest(url, credentials).get().build()) { it.toUnitResult() }
    }

    /** Asks the server to delete the app password in [credentials]. */
    public suspend fun revokeAppPassword(credentials: NextcloudCredentials): NextcloudResult<Unit> {
        val url = credentials.server.resolve("ocs/v2.php/core/apppassword")
        return httpClient.fetch(ocsRequest(url, credentials).delete().build()) { it.toUnitResult() }
    }

    private fun ocsRequest(url: HttpUrl, credentials: NextcloudCredentials): Request.Builder = Request
        .Builder()
        .url(url)
        .header(OCS_API_REQUEST_HEADER, "true")
        .header("Authorization", credentials.authorizationHeader())

    private fun readStatus(response: Response): NextcloudResult<ServerStatus> {
        val status = response.decodeJsonOrNull<StatusAnswer>()
        return when {
            status == null || !status.installed ->
                if (response.code >= HTTP_SERVER_ERROR_MIN) {
                    NextcloudResult.Failure(NextcloudError.ServerError(response.code))
                } else {
                    NextcloudResult.Failure(NextcloudError.NotNextcloud)
                }

            status.maintenance || status.needsDbUpgrade -> NextcloudResult.Failure(NextcloudError.Maintenance)

            else -> NextcloudResult.Success(ServerStatus(status.version))
        }
    }

    private fun readLoginStart(address: ServerAddress, response: Response): NextcloudResult<LoginSession> {
        if (!response.isSuccessful) {
            val error =
                if (response.code >= HTTP_SERVER_ERROR_MIN) {
                    NextcloudError.ServerError(response.code)
                } else {
                    NextcloudError.NotNextcloud
                }
            return NextcloudResult.Failure(error)
        }
        val answer = response.decodeJsonOrNull<LoginStartAnswer>()
        val urls = listOfNotNull(answer?.login?.toHttpUrlOrNull(), answer?.poll?.endpoint?.toHttpUrlOrNull())
        return if (answer == null || urls.size != 2 || !urls.all(address::hasSameOrigin)) {
            NextcloudResult.Failure(NextcloudError.NotNextcloud)
        } else {
            NextcloudResult.Success(LoginSession(address, urls[0], urls[1], answer.poll.token, clock()))
        }
    }

    private fun readPoll(session: LoginSession, response: Response): NextcloudResult<PollResult> {
        val approved = if (response.isSuccessful) response.decodeJsonOrNull<PollAnswer>() else null
        return when {
            approved != null ->
                NextcloudResult.Success(
                    PollResult.Approved(
                        NextcloudCredentials(session.address, approved.loginName, approved.appPassword),
                    ),
                )

            response.code == HTTP_NOT_FOUND -> NextcloudResult.Success(PollResult.Pending)

            else -> NextcloudResult.Failure(NextcloudError.ServerError(response.code))
        }
    }

    /** Timing of Login Flow v2. */
    public companion object {
        /** How long Nextcloud keeps a login token valid. */
        public val LOGIN_LIFETIME: Duration = 20.minutes

        /** How often [awaitApproval] polls by default. */
        public val POLL_INTERVAL: Duration = 2.seconds
    }
}

@Serializable
internal data class StatusAnswer(
    val installed: Boolean = false,
    val maintenance: Boolean = false,
    val needsDbUpgrade: Boolean = false,
    val version: String = "",
)

@Serializable
internal data class LoginStartAnswer(val poll: PollInfo, val login: String)

@Serializable
internal data class PollInfo(val token: String, val endpoint: String)

@Serializable
internal data class PollAnswer(val loginName: String, val appPassword: String)
