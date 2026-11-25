package io.github.tycho991.kombu.data.account

import io.github.tycho991.kombu.cookbookclient.CookbookClient
import io.github.tycho991.kombu.cookbookclient.CookbookVersion
import io.github.tycho991.kombu.nextcloudauth.LoginSession
import io.github.tycho991.kombu.nextcloudauth.NextcloudAuthClient
import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import io.github.tycho991.kombu.nextcloudauth.ServerStatus
import kotlinx.coroutines.channels.ReceiveChannel

/** The server calls the account logic needs; replaceable in tests. */
interface AccountApi {
    /** See [NextcloudAuthClient.checkServer]. */
    suspend fun checkServer(address: ServerAddress): NextcloudResult<ServerStatus>

    /** See [NextcloudAuthClient.startLogin]. */
    suspend fun startLogin(address: ServerAddress): NextcloudResult<LoginSession>

    /** See [NextcloudAuthClient.awaitApproval]. */
    suspend fun awaitApproval(
        session: LoginSession,
        wakeUps: ReceiveChannel<Unit>,
    ): NextcloudResult<NextcloudCredentials>

    /** See [NextcloudAuthClient.verifyAccount]. */
    suspend fun verifyAccount(credentials: NextcloudCredentials): NextcloudResult<Unit>

    /** See [CookbookClient.checkAvailable]. */
    suspend fun checkCookbook(credentials: NextcloudCredentials): NextcloudResult<CookbookVersion>

    /** See [NextcloudAuthClient.revokeAppPassword]. */
    suspend fun revokeAppPassword(credentials: NextcloudCredentials): NextcloudResult<Unit>
}

/** [AccountApi] backed by the real Nextcloud and Cookbook clients. */
class RemoteAccountApi(private val auth: NextcloudAuthClient, private val cookbook: CookbookClient) : AccountApi {
    override suspend fun checkServer(address: ServerAddress) = auth.checkServer(address)

    override suspend fun startLogin(address: ServerAddress) = auth.startLogin(address)

    override suspend fun awaitApproval(session: LoginSession, wakeUps: ReceiveChannel<Unit>) =
        auth.awaitApproval(session, wakeUps)

    override suspend fun verifyAccount(credentials: NextcloudCredentials) = auth.verifyAccount(credentials)

    override suspend fun checkCookbook(credentials: NextcloudCredentials) = cookbook.checkAvailable(credentials)

    override suspend fun revokeAppPassword(credentials: NextcloudCredentials) = auth.revokeAppPassword(credentials)
}
