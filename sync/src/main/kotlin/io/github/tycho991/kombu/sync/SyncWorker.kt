package io.github.tycho991.kombu.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Gives the [SyncWorker] the process-wide engine; the app's `Application` implements it. */
interface SyncEngineProvider {
    /** The one engine of the process, so that runs never overlap. */
    val syncEngine: SyncEngine
}

/** Runs one sync for WorkManager and maps its outcome to a work result (design D10). */
class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = resultOf((applicationContext as SyncEngineProvider).syncEngine.run())

    /** The mapping from a sync result to a work result. */
    companion object {
        /** Network and server errors are retried with backoff; rejected credentials and old servers are not. */
        fun resultOf(result: SyncResult): Result = when (result) {
            SyncResult.Success, SyncResult.NotSignedIn -> Result.success()
            is SyncResult.Failed -> if (result.retry) Result.retry() else Result.failure()
        }
    }
}
