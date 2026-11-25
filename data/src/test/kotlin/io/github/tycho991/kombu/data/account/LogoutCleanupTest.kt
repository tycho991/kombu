package io.github.tycho991.kombu.data.account

import io.github.tycho991.kombu.cookbookclient.CookbookVersion
import io.github.tycho991.kombu.data.recipes.ImageStore
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeRepository
import io.github.tycho991.kombu.data.recipes.RoomRecipeStore
import io.github.tycho991.kombu.data.recipes.inMemoryDatabase
import io.github.tycho991.kombu.data.recipes.recipe
import io.github.tycho991.kombu.data.recipes.synced
import io.github.tycho991.kombu.nextcloudauth.LoginSession
import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import io.github.tycho991.kombu.nextcloudauth.ServerStatus
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Logout with the real Room database and image store (spec nextcloud-account, "Logout revokes the app password"). */
@RunWith(RobolectricTestRunner::class)
class LogoutCleanupTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = inMemoryDatabase()
    private val credentials =
        NextcloudCredentials(ServerAddress.of("https://cloud.example.com".toHttpUrl()), "alex", "app-password")
    private lateinit var dataStore: TestDataStore

    @After
    fun close() {
        database.close()
        dataStore.close()
    }

    @Test
    fun logoutLeavesNoRecipesAndNoImageFiles() = runTest {
        val imageRoot = File(folder.root, "recipe-images")
        val images = ImageStore(imageRoot)
        val recipes = RecipeRepository(database, images)
        val store = RoomRecipeStore(database, images)
        val synced = store.insert(synced("Soup", "1", image = "https://example.org/soup.jpg"))
        store.saveImages(synced.localId, byteArrayOf(1), byteArrayOf(2))
        recipes.create(recipe("New"))
        dataStore = TestDataStore(File(folder.root, "account.preferences_pb"))
        val credentialStore = CredentialStore(dataStore.dataStore, SoftwareKeySource())
        credentialStore.save(credentials)

        AccountRepository(OfflineApi(), credentialStore, recipes).logout()

        assertEquals(emptyList<LocalRecipe>(), store.recipes())
        assertFalse(imageRoot.exists())
    }

    /** Every call fails as if the device were offline; logout still signs out locally. */
    private class OfflineApi : AccountApi {
        private val offline = NextcloudResult.Failure(NextcloudError.Unreachable)

        override suspend fun checkServer(address: ServerAddress): NextcloudResult<ServerStatus> = offline

        override suspend fun startLogin(address: ServerAddress): NextcloudResult<LoginSession> = offline

        override suspend fun awaitApproval(session: LoginSession, wakeUps: ReceiveChannel<Unit>) = offline

        override suspend fun verifyAccount(credentials: NextcloudCredentials): NextcloudResult<Unit> = offline

        override suspend fun checkCookbook(credentials: NextcloudCredentials): NextcloudResult<CookbookVersion> =
            offline

        override suspend fun revokeAppPassword(credentials: NextcloudCredentials): NextcloudResult<Unit> = offline
    }
}
