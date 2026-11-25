package io.github.tycho991.kombu.sync

import io.github.tycho991.kombu.cookbookclient.CookbookClient
import io.github.tycho991.kombu.data.recipes.LocalRecipeStore
import io.github.tycho991.kombu.data.recipes.SyncProblem
import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Syncs the local recipe copy with the Cookbook server (spec recipe-sync, design D5).
 *
 * A run checks the server, pulls server changes, pushes local changes and downloads missing images. If a local
 * change was made while it ran, it pushes once more. Runs never overlap: the app keeps one engine per process,
 * and [run] waits for a running sync to end (design D10).
 *
 * @param credentials the signed-in account, or `null` when nobody is signed in.
 * @param clock the current time in epoch milliseconds.
 */
class SyncEngine(
    private val client: CookbookClient,
    private val store: LocalRecipeStore,
    private val credentials: suspend () -> NextcloudCredentials?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()

    /** Runs one sync and records its outcome in the store. */
    suspend fun run(): SyncResult = lock.withLock {
        val account = credentials() ?: return@withLock SyncResult.NotSignedIn
        val state = store.syncState()
        if (state.credentialsRejected) return@withLock SyncResult.Failed(SyncProblem.CredentialsRejected)
        val result =
            try {
                sync(SyncSession(client, store, account, clock), state.localChangeSeq)
                SyncResult.Success
            } catch (abort: SyncAbort) {
                SyncResult.Failed(abort.problem)
            }
        when (result) {
            is SyncResult.Failed -> store.recordProblem(result.problem)
            else -> store.recordSuccess(clock())
        }
        result
    }

    /**
     * Runs [block] while no sync runs; a sync that starts meanwhile waits. Logout uses it to delete the local copy
     * without a running sync writing recipes of the old account back.
     */
    suspend fun <T> withoutSync(block: suspend () -> T): T = lock.withLock { block() }

    private suspend fun sync(session: SyncSession, localChangeSeq: Long) {
        val version = client.checkAvailable(session.credentials).orAbort()
        if (!version.isSupported) throw SyncAbort(SyncProblem.CookbookTooOld(version.toString()))
        val stubs = PullStep(session).run()
        val push = PushStep(session, stubs)
        push.run()
        ImageStep(session).run()
        if (store.syncState().localChangeSeq != localChangeSeq) {
            push.run()
            ImageStep(session).run()
        }
    }
}

/** What one run works with. */
internal class SyncSession(
    val client: CookbookClient,
    val store: LocalRecipeStore,
    val credentials: NextcloudCredentials,
    val clock: () -> Long,
)

/** Stops the run with [problem]; recipes processed before stay as they were written. */
internal class SyncAbort(val problem: SyncProblem) : Exception(problem.toString())

/** The value of a successful call; any failure stops the run. */
internal fun <T> NextcloudResult<T>.orAbort(): T = when (this) {
    is NextcloudResult.Success -> value
    is NextcloudResult.Failure -> throw SyncAbort(error.toProblem())
}

/** The value of a successful call, `null` for [NextcloudError.NotFound]; any other failure stops the run. */
internal fun <T> NextcloudResult<T>.orNullIfMissing(): T? =
    if (this is NextcloudResult.Failure && error == NextcloudError.NotFound) null else orAbort()
