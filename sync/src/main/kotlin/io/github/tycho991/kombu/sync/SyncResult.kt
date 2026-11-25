package io.github.tycho991.kombu.sync

import io.github.tycho991.kombu.data.recipes.SyncProblem
import io.github.tycho991.kombu.nextcloudauth.NextcloudError

/** How a sync run ended. */
sealed interface SyncResult {
    /** The run completed; local copy and server agree, except for conflicts and refused uploads. */
    data object Success : SyncResult

    /** Nobody is signed in, so there was nothing to sync. */
    data object NotSignedIn : SyncResult

    /** The run stopped because of [problem]; recipes it already processed stay updated. */
    data class Failed(
        /** Why the run stopped. */
        val problem: SyncProblem,
    ) : SyncResult {
        /** Whether a later attempt may succeed by itself: true for network and server errors (design D10). */
        val retry: Boolean get() = problem is SyncProblem.Offline || problem is SyncProblem.ServerError
    }
}

/** Maps an error that stops a whole run to the problem shown to the user. */
internal fun NextcloudError.toProblem(): SyncProblem = when (this) {
    NextcloudError.Unauthorized -> SyncProblem.CredentialsRejected
    NextcloudError.Unreachable, NextcloudError.CertificateRejected -> SyncProblem.Offline
    NextcloudError.CookbookMissing -> SyncProblem.CookbookMissing
    is NextcloudError.CookbookTooOld -> SyncProblem.CookbookTooOld(version)
    is NextcloudError.ServerError -> SyncProblem.ServerError(statusCode)
    NextcloudError.NotFound -> SyncProblem.ServerError(HTTP_NOT_FOUND)
    else -> SyncProblem.ServerError(0)
}

private const val HTTP_NOT_FOUND = 404
