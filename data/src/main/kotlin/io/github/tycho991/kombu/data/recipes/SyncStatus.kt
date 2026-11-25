package io.github.tycho991.kombu.data.recipes

/** Why the last sync did not complete (design D12); the app explains each kind to the user. */
sealed interface SyncProblem {
    /** The server could not be reached. */
    data object Offline : SyncProblem

    /** The server answered with an error, for example during maintenance. */
    data class ServerError(
        /** The HTTP status code, or `0` when the answer was unreadable. */
        val statusCode: Int,
    ) : SyncProblem

    /** The server rejected the stored credentials; the user must sign in again. */
    data object CredentialsRejected : SyncProblem

    /** The Cookbook app is not installed or not enabled. */
    data object CookbookMissing : SyncProblem

    /** The Cookbook app is older than 0.10.3. */
    data class CookbookTooOld(
        /** The installed version as text. */
        val version: String,
    ) : SyncProblem
}

/** What the start screen shows about the local copy and the last sync (spec "Sync status"). */
data class SyncStatus(
    /** Recipes in the local copy, without the ones deleted here. */
    val recipeCount: Int = 0,
    /** Recipes with a local change that is not uploaded yet. */
    val pendingCount: Int = 0,
    /** Recipes in conflict. */
    val conflictCount: Int = 0,
    /** When the last sync completed (epoch milliseconds), or `null` if none did. */
    val lastSuccessAt: Long? = null,
    /** Why the last sync failed, or `null` when it completed. */
    val problem: SyncProblem? = null,
    /** Grows with every local change; the app schedules a sync when it grows. */
    val localChangeSeq: Long = 0,
)

/** The sync bookkeeping the engine reads at the start of a run. */
data class SyncState(
    /** The server rejected the credentials; no request may be sent until the user signs in again. */
    val credentialsRejected: Boolean = false,
    /** Grows with every local change; a run that sees it grow uploads once more (design D5). */
    val localChangeSeq: Long = 0,
)
