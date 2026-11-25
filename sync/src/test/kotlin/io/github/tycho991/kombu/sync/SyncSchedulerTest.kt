package io.github.tycho991.kombu.sync

import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = TestSyncApplication::class)
class SyncSchedulerTest {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: SyncScheduler

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = SyncScheduler(workManager)
    }

    private fun work(name: String): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(name).get()

    private fun pending(name: String): List<WorkInfo> = work(name).filter { !it.state.isFinished }

    @Test
    fun startSyncsWithNetworkAndEveryHour() {
        scheduler.start()

        val triggered = pending(SyncScheduler.TRIGGERED).single()
        assertEquals(NetworkType.CONNECTED, triggered.constraints.requiredNetworkType)
        assertEquals(0L, triggered.initialDelayMillis)
        val periodic = pending(SyncScheduler.PERIODIC).single()
        assertEquals(NetworkType.CONNECTED, periodic.constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(1), periodic.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun aLocalChangeSyncsAfterADelayWithNetwork() {
        scheduler.afterLocalChange()

        val triggered = pending(SyncScheduler.TRIGGERED).single()
        assertEquals(TimeUnit.SECONDS.toMillis(10), triggered.initialDelayMillis)
        assertEquals(NetworkType.CONNECTED, triggered.constraints.requiredNetworkType)
    }

    @Test
    fun triggersWhileOneIsWaitingAreDropped() {
        scheduler.afterLocalChange()
        val first = pending(SyncScheduler.TRIGGERED).single().id

        scheduler.afterLocalChange()
        scheduler.start()
        scheduler.start()

        assertEquals(first, pending(SyncScheduler.TRIGGERED).single().id)
        assertEquals(1, pending(SyncScheduler.PERIODIC).size)
    }

    @Test
    fun syncNowNeedsNoNetwork() {
        scheduler.syncNow()

        // Without a network constraint, the synchronous test executor runs it at once.
        val manual = work(SyncScheduler.MANUAL).single()
        assertEquals(NetworkType.NOT_REQUIRED, manual.constraints.requiredNetworkType)
        assertEquals(WorkInfo.State.SUCCEEDED, manual.state)
    }

    @Test
    fun cancelAllStopsEveryTrigger() {
        scheduler.start()
        scheduler.afterLocalChange()

        scheduler.cancelAll()

        listOf(SyncScheduler.TRIGGERED, SyncScheduler.PERIODIC, SyncScheduler.MANUAL).forEach { name ->
            assertEquals(name, emptyList<WorkInfo>(), pending(name))
        }
    }

    @Test
    fun reportsNoRunningSyncWhenNoneRuns() = runTest {
        scheduler.start()

        assertFalse(scheduler.running.first())
    }
}
