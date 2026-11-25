package io.github.tycho991.kombu.sync

import android.app.Application
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.github.tycho991.kombu.cookbookclient.CookbookClient
import io.github.tycho991.kombu.data.recipes.SyncProblem
import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** An application that gives the worker an engine, like the app's own `Application` does. */
class TestSyncApplication :
    Application(),
    SyncEngineProvider {
    val store = InMemoryRecipeStore()
    var signedIn = false
    override val syncEngine: SyncEngine =
        SyncEngine(CookbookClient(OkHttpClient()), store, {
            NextcloudCredentials(ServerAddress.of("https://cloud.example.com".toHttpUrl()), "alex", "secret")
                .takeIf { signedIn }
        })
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestSyncApplication::class)
class SyncWorkerTest {
    private val app get() = RuntimeEnvironment.getApplication() as TestSyncApplication

    @Test
    fun mapsEverySyncResult() {
        val cases =
            mapOf(
                SyncResult.Success to ListenableWorker.Result.success(),
                SyncResult.NotSignedIn to ListenableWorker.Result.success(),
                SyncResult.Failed(SyncProblem.Offline) to ListenableWorker.Result.retry(),
                SyncResult.Failed(SyncProblem.ServerError(503)) to ListenableWorker.Result.retry(),
                SyncResult.Failed(SyncProblem.CredentialsRejected) to ListenableWorker.Result.failure(),
                SyncResult.Failed(SyncProblem.CookbookMissing) to ListenableWorker.Result.failure(),
                SyncResult.Failed(SyncProblem.CookbookTooOld("0.10.2")) to ListenableWorker.Result.failure(),
            )
        cases.forEach { (result, expected) -> assertEquals(result.toString(), expected, SyncWorker.resultOf(result)) }
    }

    @Test
    fun runsTheEngineOfTheApplication() = runTest {
        val worker = TestListenableWorkerBuilder<SyncWorker>(app).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        app.signedIn = true
        app.store.recordProblem(SyncProblem.CredentialsRejected)
        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }
}
