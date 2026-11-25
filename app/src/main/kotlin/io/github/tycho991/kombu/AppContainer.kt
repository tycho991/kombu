package io.github.tycho991.kombu

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import io.github.tycho991.kombu.cookbookclient.CookbookClient
import io.github.tycho991.kombu.data.account.ACCOUNT_DATASTORE_NAME
import io.github.tycho991.kombu.data.account.AccountLocalData
import io.github.tycho991.kombu.data.account.AccountRepository
import io.github.tycho991.kombu.data.account.AccountService
import io.github.tycho991.kombu.data.account.AndroidKeystoreKeySource
import io.github.tycho991.kombu.data.account.CredentialStore
import io.github.tycho991.kombu.data.account.RemoteAccountApi
import io.github.tycho991.kombu.data.recipes.ImageStore
import io.github.tycho991.kombu.data.recipes.RecipeRepository
import io.github.tycho991.kombu.data.recipes.RoomRecipeStore
import io.github.tycho991.kombu.data.recipes.db.KombuDatabase
import io.github.tycho991.kombu.nextcloudauth.KOMBU_USER_AGENT
import io.github.tycho991.kombu.nextcloudauth.NextcloudAuthClient
import io.github.tycho991.kombu.sync.SyncControl
import io.github.tycho991.kombu.sync.SyncEngine
import io.github.tycho991.kombu.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** The app's shared objects, created once per process (design D11 of nextcloud-login, D10 of recipe-sync). */
class AppContainer(context: Context) {
    /** The HTTP client for every Nextcloud request; it sends Kombu's user agent and logs nothing. */
    val httpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(
                Interceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", KOMBU_USER_AGENT).build())
                },
            ).build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database: KombuDatabase = KombuDatabase.open(context)
    private val images = ImageStore(File(context.noBackupFilesDir, RECIPE_IMAGE_DIRECTORY))

    /** The local recipe copy; its images live in the no-backup directory (design D8). */
    val recipes: RecipeRepository = RecipeRepository(database, images)

    /** Starts and stops syncs through WorkManager. */
    val sync: SyncControl = SyncScheduler(WorkManager.getInstance(context))

    /** The one sync engine of the process; the sync worker gets it through the application. */
    val syncEngine: SyncEngine =
        SyncEngine(CookbookClient(httpClient), RoomRecipeStore(database, images), { accounts.account.first() })

    /** On logout, no sync may run or start while the local copy is deleted (design D13). */
    private val accountData =
        object : AccountLocalData by recipes {
            override suspend fun clearAll() {
                sync.cancelAll()
                syncEngine.withoutSync { recipes.clearAll() }
            }
        }

    /** Signing in and out. */
    val accounts: AccountService =
        AccountRepository(
            api = RemoteAccountApi(NextcloudAuthClient(httpClient), CookbookClient(httpClient)),
            store =
            CredentialStore(
                dataStore =
                PreferenceDataStoreFactory.create(
                    produceFile = { context.preferencesDataStoreFile(ACCOUNT_DATASTORE_NAME) },
                ),
                keySource = AndroidKeystoreKeySource(),
            ),
            localData = accountData,
        )

    init {
        // App start, login and a new login to the same account sync at once and then hourly; logout stops it.
        scope.launch {
            accounts.account.distinctUntilChanged().collect { account ->
                if (account != null) sync.start() else sync.cancelAll()
            }
        }
        // Every local change leads to a sync shortly after.
        scope.launch {
            recipes
                .status()
                .map { it.localChangeSeq }
                .distinctUntilChanged()
                .drop(1)
                .collect { sync.afterLocalChange() }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 30L
        const val RECIPE_IMAGE_DIRECTORY = "recipe-images"
    }
}
