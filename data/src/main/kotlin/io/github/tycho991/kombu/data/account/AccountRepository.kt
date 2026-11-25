package io.github.tycho991.kombu.data.account

import io.github.tycho991.kombu.nextcloudauth.LoginSession
import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import io.github.tycho991.kombu.nextcloudauth.ParsedAddress
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How a logout ended (spec nextcloud-account, "Logout revokes the app password"). */
enum class LogoutResult {
    /** The app password was revoked on the server, or the server no longer accepted it. */
    REVOKED,

    /** Signed out locally; the server could not be asked, so the app password is still listed there. */
    LOCAL_ONLY,
}

/** Signing in and out, as the app uses it; [AccountRepository] is the implementation. */
interface AccountService {
    /** The signed-in account, or `null`. */
    val account: Flow<NextcloudCredentials?>

    /** Parses [input], checks the server and starts Login Flow v2. */
    suspend fun startLogin(input: String): NextcloudResult<LoginSession>

    /** Waits for the approval of [session], checks the account and stores the credentials. */
    suspend fun completeLogin(
        session: LoginSession,
        wakeUps: ReceiveChannel<Unit>,
    ): NextcloudResult<NextcloudCredentials>

    /** Signs out; returns `null` when nobody was signed in. */
    suspend fun logout(): LogoutResult?
}

/** Signs the user in and out and exposes the current account. */
class AccountRepository(
    private val api: AccountApi,
    private val store: CredentialStore,
    private val localData: AccountLocalData,
    private val revokeTimeout: Duration = REVOKE_TIMEOUT,
) : AccountService {
    override val account: Flow<NextcloudCredentials?> = store.credentials

    override suspend fun startLogin(input: String): NextcloudResult<LoginSession> =
        when (val parsed = ServerAddress.parse(input)) {
            is ParsedAddress.Rejected -> NextcloudResult.Failure(NextcloudError.InvalidAddress(parsed.reason))

            is ParsedAddress.Accepted ->
                when (val status = api.checkServer(parsed.address)) {
                    is NextcloudResult.Failure -> status
                    is NextcloudResult.Success -> api.startLogin(parsed.address)
                }
        }

    /**
     * Waits for the approval of [session], checks the account and the Cookbook app (0.10.3 or newer), and
     * stores the credentials only when both checks pass. When a check fails, the new app password is revoked
     * again if possible.
     */
    override suspend fun completeLogin(
        session: LoginSession,
        wakeUps: ReceiveChannel<Unit>,
    ): NextcloudResult<NextcloudCredentials> {
        val approval = api.awaitApproval(session, wakeUps)
        if (approval is NextcloudResult.Failure) return approval
        return checkAndStore((approval as NextcloudResult.Success).value)
    }

    /**
     * Signs in again after the server rejected the stored credentials (design D13). Waits for the approval of
     * [session]; if the new login is for the same server and login name, it replaces the credentials and keeps
     * all local data. A login for another account is revoked again and gives [NextcloudError.DifferentAccount].
     */
    suspend fun reauthenticate(
        session: LoginSession,
        wakeUps: ReceiveChannel<Unit>,
    ): NextcloudResult<NextcloudCredentials> {
        val approval = api.awaitApproval(session, wakeUps)
        if (approval !is NextcloudResult.Success) return approval
        val credentials = approval.value
        val current = store.credentials.first()
        val sameAccount = current?.server == credentials.server && current.loginName == credentials.loginName
        return if (sameAccount) {
            checkAndStore(credentials).also { if (it is NextcloudResult.Success) localData.credentialsAccepted() }
        } else {
            revokeQuietly(credentials)
            NextcloudResult.Failure(NextcloudError.DifferentAccount)
        }
    }

    private suspend fun checkAndStore(credentials: NextcloudCredentials): NextcloudResult<NextcloudCredentials> {
        val failure = checkAccount(credentials)
        return if (failure == null) {
            store.save(credentials)
            NextcloudResult.Success(credentials)
        } else {
            if (failure.error != NextcloudError.Unauthorized) revokeQuietly(credentials)
            failure
        }
    }

    /**
     * Deletes the stored credentials and the local recipe copy, then asks the server to revoke the app password
     * (design D12, D13). Returns `null` when nobody was signed in.
     */
    override suspend fun logout(): LogoutResult? {
        val credentials = store.credentials.first() ?: return null
        store.clear()
        localData.clearAll()
        val revoked = withTimeoutOrNull(revokeTimeout) { api.revokeAppPassword(credentials) }
        val accepted =
            revoked is NextcloudResult.Success ||
                (revoked as? NextcloudResult.Failure)?.error == NextcloudError.Unauthorized
        return if (accepted) LogoutResult.REVOKED else LogoutResult.LOCAL_ONLY
    }

    private suspend fun checkAccount(credentials: NextcloudCredentials): NextcloudResult.Failure? {
        val account = api.verifyAccount(credentials)
        if (account is NextcloudResult.Failure) return account
        return when (val cookbook = api.checkCookbook(credentials)) {
            is NextcloudResult.Failure -> cookbook

            is NextcloudResult.Success ->
                if (cookbook.value.isSupported) {
                    null
                } else {
                    NextcloudResult.Failure(NextcloudError.CookbookTooOld(cookbook.value.toString()))
                }
        }
    }

    private suspend fun revokeQuietly(credentials: NextcloudCredentials) {
        withContext(NonCancellable) {
            withTimeoutOrNull(revokeTimeout) { api.revokeAppPassword(credentials) }
        }
    }

    private companion object {
        val REVOKE_TIMEOUT: Duration = 10.seconds
    }
}
