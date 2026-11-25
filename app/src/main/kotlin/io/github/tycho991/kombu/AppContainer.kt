package io.github.tycho991.kombu

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.tycho991.kombu.cookbookclient.CookbookClient
import io.github.tycho991.kombu.data.account.ACCOUNT_DATASTORE_NAME
import io.github.tycho991.kombu.data.account.AccountRepository
import io.github.tycho991.kombu.data.account.AccountService
import io.github.tycho991.kombu.data.account.AndroidKeystoreKeySource
import io.github.tycho991.kombu.data.account.CredentialStore
import io.github.tycho991.kombu.data.account.RemoteAccountApi
import io.github.tycho991.kombu.nextcloudauth.KOMBU_USER_AGENT
import io.github.tycho991.kombu.nextcloudauth.NextcloudAuthClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The app's shared objects, created once per process (design D11). */
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
        )

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 30L
    }
}
