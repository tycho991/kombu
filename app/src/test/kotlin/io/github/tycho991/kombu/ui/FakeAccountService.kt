package io.github.tycho991.kombu.ui

import io.github.tycho991.kombu.data.account.AccountService
import io.github.tycho991.kombu.data.account.LogoutResult
import io.github.tycho991.kombu.nextcloudauth.LoginSession
import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl

val testAddress = ServerAddress.of("https://cloud.example.com".toHttpUrl())
val testCredentials = NextcloudCredentials(testAddress, "alex", "app-password")
val testSession =
    LoginSession(
        address = testAddress,
        loginUrl = "https://cloud.example.com/login/v2/flow/abc".toHttpUrl(),
        pollEndpoint = "https://cloud.example.com/login/v2/poll".toHttpUrl(),
        token = "poll-token",
        startedAtMillis = 1_000L,
    )

/** An [AccountService] whose answers the test controls. */
class FakeAccountService(signedIn: NextcloudCredentials? = null) : AccountService {
    val accountState = MutableStateFlow(signedIn)
    var startResult: NextcloudResult<LoginSession> = NextcloudResult.Success(testSession)
    var completion = CompletableDeferred<NextcloudResult<NextcloudCredentials>>()
    var logoutResult: LogoutResult? = LogoutResult.REVOKED
    val startInputs = mutableListOf<String>()
    val completedSessions = mutableListOf<LoginSession>()
    var lastWakeUps: ReceiveChannel<Unit>? = null

    override val account = accountState

    override suspend fun startLogin(input: String): NextcloudResult<LoginSession> {
        startInputs += input
        return startResult
    }

    override suspend fun completeLogin(
        session: LoginSession,
        wakeUps: ReceiveChannel<Unit>,
    ): NextcloudResult<NextcloudCredentials> {
        completedSessions += session
        lastWakeUps = wakeUps
        val result = completion.await()
        if (result is NextcloudResult.Success) accountState.value = result.value
        return result
    }

    val reauthenticatedSessions = mutableListOf<LoginSession>()

    /** Like Kombu, a new login replaces the credentials only on success; [completion] decides the outcome. */
    override suspend fun reauthenticate(
        session: LoginSession,
        wakeUps: ReceiveChannel<Unit>,
    ): NextcloudResult<NextcloudCredentials> {
        reauthenticatedSessions += session
        val result = completion.await()
        if (result is NextcloudResult.Success) accountState.value = result.value
        return result
    }

    override suspend fun logout(): LogoutResult? {
        accountState.value = null
        return logoutResult
    }
}
