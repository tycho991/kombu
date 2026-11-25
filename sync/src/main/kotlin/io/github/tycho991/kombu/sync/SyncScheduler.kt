package io.github.tycho991.kombu.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.TimeUnit

/** Starting and stopping syncs, as the app uses it; [SyncScheduler] implements it. */
interface SyncControl {
    /** Whether a sync runs right now. */
    val running: Flow<Boolean>

    /** Syncs now, and about every hour from now on while a network is available (spec "Sync triggers"). */
    fun start()

    /** Syncs shortly after a local change. */
    fun afterLocalChange()

    /** Syncs at once, also without network, so the user sees the offline problem right away. */
    fun syncNow()

    /** Cancels every scheduled and running sync, for example on logout. */
    fun cancelAll()
}

/** [SyncControl] on top of WorkManager (design D10). */
class SyncScheduler(private val workManager: WorkManager) : SyncControl {
    override val running: Flow<Boolean> =
        combine(UNIQUE_NAMES.map { workManager.getWorkInfosForUniqueWorkFlow(it) }) { infos ->
            infos.any { list -> list.any { it.state == WorkInfo.State.RUNNING } }
        }.distinctUntilChanged()

    override fun start() {
        workManager.enqueueUniqueWork(TRIGGERED, ExistingWorkPolicy.KEEP, oneTime(withNetwork))
        val periodic =
            PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                .setConstraints(withNetwork)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
    }

    override fun afterLocalChange() {
        val request = oneTime(withNetwork) { setInitialDelay(CHANGE_DELAY_SECONDS, TimeUnit.SECONDS) }
        workManager.enqueueUniqueWork(TRIGGERED, ExistingWorkPolicy.KEEP, request)
    }

    override fun syncNow() {
        workManager.enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP, oneTime(Constraints.NONE))
    }

    override fun cancelAll() {
        UNIQUE_NAMES.forEach(workManager::cancelUniqueWork)
    }

    private fun oneTime(
        constraints: Constraints,
        configure: OneTimeWorkRequest.Builder.() -> Unit = {},
    ): OneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
        .apply(configure)
        .build()

    /** Unique work names and timings of the triggers. */
    companion object {
        /** App start and local changes; a trigger while one is queued or running is dropped (`KEEP`). */
        const val TRIGGERED = "recipe-sync"

        /** The user's "sync now". */
        const val MANUAL = "recipe-sync-manual"

        /** The hourly sync. */
        const val PERIODIC = "recipe-sync-periodic"

        /** Hours between periodic syncs. */
        const val PERIOD_HOURS = 1L

        /** Delay after a local change, so that a burst of edits leads to one sync. */
        const val CHANGE_DELAY_SECONDS = 10L

        /** First retry delay after a network or server error; it grows exponentially. */
        const val BACKOFF_SECONDS = 30L

        private val UNIQUE_NAMES = listOf(TRIGGERED, MANUAL, PERIODIC)

        private val withNetwork: Constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}
