package io.github.tycho991.kombu.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.BrowsePreferences
import io.github.tycho991.kombu.data.recipes.ImageStore
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeRepository
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.RoomRecipeStore
import io.github.tycho991.kombu.data.recipes.SyncStatus
import io.github.tycho991.kombu.data.recipes.db.KombuDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.nio.file.Files

/**
 * The app's data layer for UI tests: an in-memory recipe database, a temporary image directory and DataStore,
 * a [FakeSyncControl], and the [dependencies] to pass to the screens. Call [close] after the test.
 */
class TestApp(
    val accounts: FakeAccountService = FakeAccountService(signedIn = testCredentials),
    status: Flow<SyncStatus>? = null,
) : AutoCloseable {
    private val directory: File = Files.createTempDirectory("kombu-test").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: KombuDatabase =
        Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KombuDatabase::class.java).build()
    val images = ImageStore(File(directory, "images"))
    val recipes = RecipeRepository(database, images)
    val store = RoomRecipeStore(database, images)
    val browse = BrowsePreferences(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(directory, "browse.preferences_pb") }),
    )
    val sync = FakeSyncControl()
    val dependencies = KombuDependencies(accounts, recipes, browse, sync, status ?: recipes.status())

    /** Stores a synced recipe from [json] and returns it with its local id. */
    fun insert(json: String, state: RecipeState = RecipeState.SYNCED, remoteId: String? = "r${json.hashCode()}") =
        runBlocking {
            val recipe = RemoteRecipe(Json.parseToJsonElement(json) as JsonObject)
            store.insert(
                if (state == RecipeState.CREATED) {
                    LocalRecipe(local = recipe, state = state)
                } else {
                    LocalRecipe(remoteId = remoteId, base = recipe, state = state)
                },
            )
        }

    override fun close() {
        database.close()
        scope.cancel()
        directory.deleteRecursively()
    }
}

/**
 * Creates [TestApp]s and closes them after the test. Order it outside the compose rule, so the database is closed
 * only after the composition that reads it is gone.
 */
class TestAppRule : org.junit.rules.ExternalResource() {
    private val created = mutableListOf<TestApp>()

    /** A new [TestApp], closed after the test. */
    fun create(
        accounts: FakeAccountService = FakeAccountService(signedIn = testCredentials),
        status: Flow<SyncStatus>? = null,
    ): TestApp = TestApp(accounts, status).also { created += it }

    override fun after() {
        created.forEach(TestApp::close)
    }
}
