package io.github.tycho991.kombu.data.account

import io.github.tycho991.kombu.cookbookclient.CookbookVersion
import io.github.tycho991.kombu.nextcloudauth.AddressError
import io.github.tycho991.kombu.nextcloudauth.LoginSession
import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import io.github.tycho991.kombu.nextcloudauth.ServerStatus
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AccountRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val address = ServerAddress.of("https://cloud.example.com".toHttpUrl())
    private val credentials = NextcloudCredentials(address, "alex", "app-password")
    private val session =
        LoginSession(
            address = address,
            loginUrl = "https://cloud.example.com/login/v2/flow/abc".toHttpUrl(),
            pollEndpoint = "https://cloud.example.com/login/v2/poll".toHttpUrl(),
            token = "token",
            startedAtMillis = 0,
        )
    private val api = FakeAccountApi()
    private val localData = FakeLocalData()
    private lateinit var open: TestDataStore
    private lateinit var store: CredentialStore
    private lateinit var repository: AccountRepository

    @Before
    fun setUp() {
        open = TestDataStore(File(folder.root, "account.preferences_pb"))
        store = CredentialStore(open.dataStore, SoftwareKeySource())
        repository = AccountRepository(api, store, localData)
    }

    @After
    fun tearDown() {
        open.close()
    }

    @Test
    fun rejectsAnInvalidAddressWithoutContactingTheServer() = runTest {
        val result = repository.startLogin("http://cloud.example.com")

        assertEquals(NextcloudResult.Failure(NextcloudError.InvalidAddress(AddressError.CLEARTEXT)), result)
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test
    fun stopsWhenTheServerCheckFails() = runTest {
        api.serverCheck = NextcloudResult.Failure(NextcloudError.Maintenance)

        assertEquals(
            NextcloudResult.Failure(NextcloudError.Maintenance),
            repository.startLogin("cloud.example.com"),
        )
        assertEquals(listOf("checkServer"), api.calls)
    }

    @Test
    fun startsTheLoginAfterTheServerCheck() = runTest {
        api.loginStart = NextcloudResult.Success(session)

        assertEquals(NextcloudResult.Success(session), repository.startLogin(" cloud.example.com/ "))
        assertEquals(listOf("checkServer", "startLogin"), api.calls)
        assertEquals(address, api.lastAddress)
    }

    @Test
    fun storesCredentialsWhenAllChecksPass() = runTest {
        assertEquals(NextcloudResult.Success(credentials), repository.completeLogin(session, Channel()))
        assertEquals(credentials, repository.account.first())
        assertEquals(listOf("awaitApproval", "verifyAccount", "checkCookbook"), api.calls)
    }

    @Test
    fun storesNothingWhenTheLoginExpires() = runTest {
        api.approval = NextcloudResult.Failure(NextcloudError.LoginExpired)

        assertEquals(
            NextcloudResult.Failure(NextcloudError.LoginExpired),
            repository.completeLogin(session, Channel()),
        )
        assertNull(repository.account.first())
        assertEquals(listOf("awaitApproval"), api.calls)
    }

    @Test
    fun storesNothingWhenTheCredentialsAreRejected() = runTest {
        api.account = NextcloudResult.Failure(NextcloudError.Unauthorized)

        assertEquals(
            NextcloudResult.Failure(NextcloudError.Unauthorized),
            repository.completeLogin(session, Channel()),
        )
        assertNull(repository.account.first())
        assertEquals(listOf("awaitApproval", "verifyAccount"), api.calls)
    }

    @Test
    fun revokesTheNewAppPasswordWhenCookbookIsMissing() = runTest {
        api.cookbook = NextcloudResult.Failure(NextcloudError.CookbookMissing)

        assertEquals(
            NextcloudResult.Failure(NextcloudError.CookbookMissing),
            repository.completeLogin(session, Channel()),
        )
        assertNull(repository.account.first())
        assertEquals(listOf("awaitApproval", "verifyAccount", "checkCookbook", "revokeAppPassword"), api.calls)
    }

    @Test
    fun rejectsATooOldCookbookAndRevokesTheNewAppPassword() = runTest {
        api.cookbook = NextcloudResult.Success(CookbookVersion(listOf(0, 10, 2)))

        assertEquals(
            NextcloudResult.Failure(NextcloudError.CookbookTooOld("0.10.2")),
            repository.completeLogin(session, Channel()),
        )
        assertNull(repository.account.first())
        assertEquals(listOf("awaitApproval", "verifyAccount", "checkCookbook", "revokeAppPassword"), api.calls)
    }

    @Test
    fun acceptsTheOldestSupportedCookbook() = runTest {
        api.cookbook = NextcloudResult.Success(CookbookVersion(listOf(0, 10, 3)))

        assertEquals(NextcloudResult.Success(credentials), repository.completeLogin(session, Channel()))
        assertEquals(credentials, repository.account.first())
    }

    @Test
    fun logoutRevokesTheAppPassword() = runTest {
        store.save(credentials)

        assertEquals(LogoutResult.REVOKED, repository.logout())
        assertNull(repository.account.first())
        assertEquals(listOf("revokeAppPassword"), api.calls)
        assertEquals(listOf("clearAll"), localData.calls)
        assertEquals(credentials, api.lastCredentials)
    }

    @Test
    fun logoutCountsAnAlreadyInvalidPasswordAsRevoked() = runTest {
        store.save(credentials)
        api.revocation = NextcloudResult.Failure(NextcloudError.Unauthorized)

        assertEquals(LogoutResult.REVOKED, repository.logout())
    }

    @Test
    fun logoutOfflineSignsOutLocally() = runTest {
        store.save(credentials)
        api.revocation = NextcloudResult.Failure(NextcloudError.Unreachable)

        assertEquals(LogoutResult.LOCAL_ONLY, repository.logout())
        assertNull(repository.account.first())
        assertEquals(listOf("clearAll"), localData.calls)
    }

    @Test
    fun logoutGivesUpOnAHangingServer() = runTest {
        store.save(credentials)
        api.hangOnRevoke = true

        assertEquals(LogoutResult.LOCAL_ONLY, repository.logout())
        assertNull(repository.account.first())
    }

    @Test
    fun logoutWithoutAccountDoesNothing() = runTest {
        assertNull(repository.logout())
        assertEquals(emptyList<String>(), api.calls)
        assertEquals(emptyList<String>(), localData.calls)
    }

    @Test
    fun reauthenticationToTheSameAccountReplacesTheCredentialsAndKeepsLocalData() = runTest {
        store.save(credentials.copy(appPassword = "rejected-password"))

        assertEquals(NextcloudResult.Success(credentials), repository.reauthenticate(session, Channel()))
        assertEquals(credentials, repository.account.first())
        assertEquals(listOf("credentialsAccepted"), localData.calls)
        assertEquals(listOf("awaitApproval", "verifyAccount", "checkCookbook"), api.calls)
    }

    @Test
    fun reauthenticationWithAnotherLoginNameIsRevokedAndStoresNothing() = runTest {
        val old = credentials.copy(loginName = "sam", appPassword = "rejected-password")
        store.save(old)

        assertEquals(
            NextcloudResult.Failure(NextcloudError.DifferentAccount),
            repository.reauthenticate(session, Channel()),
        )
        assertEquals(old, repository.account.first())
        assertEquals(emptyList<String>(), localData.calls)
        assertEquals(listOf("awaitApproval", "revokeAppPassword"), api.calls)
        assertEquals(credentials, api.lastCredentials)
    }

    @Test
    fun reauthenticationOnAnotherServerIsRevoked() = runTest {
        val old = NextcloudCredentials(ServerAddress.of("https://other.example.com".toHttpUrl()), "alex", "old")
        store.save(old)

        assertEquals(
            NextcloudResult.Failure(NextcloudError.DifferentAccount),
            repository.reauthenticate(session, Channel()),
        )
        assertEquals(old, repository.account.first())
    }

    @Test
    fun reauthenticationKeepsTheOldStateWhenTheCheckFails() = runTest {
        val old = credentials.copy(appPassword = "rejected-password")
        store.save(old)
        api.cookbook = NextcloudResult.Failure(NextcloudError.Unreachable)

        assertEquals(
            NextcloudResult.Failure(NextcloudError.Unreachable),
            repository.reauthenticate(session, Channel()),
        )
        assertEquals(old, repository.account.first())
        assertEquals(emptyList<String>(), localData.calls)
    }

    @Test
    fun reauthenticationPassesOnAnUnapprovedLogin() = runTest {
        store.save(credentials)
        api.approval = NextcloudResult.Failure(NextcloudError.LoginExpired)

        assertEquals(
            NextcloudResult.Failure(NextcloudError.LoginExpired),
            repository.reauthenticate(session, Channel()),
        )
        assertEquals(listOf("awaitApproval"), api.calls)
    }

    private class FakeLocalData : AccountLocalData {
        val calls = mutableListOf<String>()

        override suspend fun unsyncedCount(): Int = 0

        override suspend fun clearAll() {
            calls += "clearAll"
        }

        override suspend fun credentialsAccepted() {
            calls += "credentialsAccepted"
        }
    }

    private inner class FakeAccountApi : AccountApi {
        val calls = mutableListOf<String>()
        var lastAddress: ServerAddress? = null
        var lastCredentials: NextcloudCredentials? = null
        var serverCheck: NextcloudResult<ServerStatus> = NextcloudResult.Success(ServerStatus("31.0.0"))
        var loginStart: NextcloudResult<LoginSession> = NextcloudResult.Success(session)
        var approval: NextcloudResult<NextcloudCredentials> = NextcloudResult.Success(credentials)
        var account: NextcloudResult<Unit> = NextcloudResult.Success(Unit)
        var cookbook: NextcloudResult<CookbookVersion> = NextcloudResult.Success(CookbookVersion(listOf(0, 11)))
        var revocation: NextcloudResult<Unit> = NextcloudResult.Success(Unit)
        var hangOnRevoke = false

        override suspend fun checkServer(address: ServerAddress): NextcloudResult<ServerStatus> {
            calls += "checkServer"
            lastAddress = address
            return serverCheck
        }

        override suspend fun startLogin(address: ServerAddress): NextcloudResult<LoginSession> {
            calls += "startLogin"
            lastAddress = address
            return loginStart
        }

        override suspend fun awaitApproval(
            session: LoginSession,
            wakeUps: ReceiveChannel<Unit>,
        ): NextcloudResult<NextcloudCredentials> {
            calls += "awaitApproval"
            return approval
        }

        override suspend fun verifyAccount(credentials: NextcloudCredentials): NextcloudResult<Unit> {
            calls += "verifyAccount"
            return account
        }

        override suspend fun checkCookbook(credentials: NextcloudCredentials): NextcloudResult<CookbookVersion> {
            calls += "checkCookbook"
            return cookbook
        }

        override suspend fun revokeAppPassword(credentials: NextcloudCredentials): NextcloudResult<Unit> {
            calls += "revokeAppPassword"
            lastCredentials = credentials
            if (hangOnRevoke) awaitCancellation()
            return revocation
        }
    }
}
