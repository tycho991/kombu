package io.github.tycho991.kombu.data.recipes.db

import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.SyncProblem

internal fun RecipeEntity.toModel(): LocalRecipe = LocalRecipe(
    localId = localId,
    remoteId = remoteId,
    base = baseJson?.let(::parseStored),
    baseModified = baseModified,
    local = localJson?.let(::parseStored),
    state = state,
    conflict = conflict,
    server = serverJson?.let(::parseStored),
    serverModified = serverModified,
    uploadError = uploadError,
    imageState = imageState,
    localChangeAt = localChangeAt,
)

/** The row for [recipe]; the extracted columns come from the version the app shows. */
internal fun LocalRecipe.toEntity(): RecipeEntity {
    val shown = shown
    return RecipeEntity(
        localId = localId,
        remoteId = remoteId,
        baseJson = base?.encode(),
        baseHash = baseHash,
        baseModified = baseModified,
        localJson = local?.encode(),
        state = state,
        conflict = conflict,
        serverJson = server?.encode(),
        serverModified = serverModified,
        uploadError = uploadError,
        name = shown?.name.orEmpty(),
        category = shown?.category,
        keywords = shown?.keywords.orEmpty().joinToString(","),
        imageState = imageState,
        localChangeAt = localChangeAt,
    )
}

/** Kombu wrote this text itself from a JSON object, so it always parses. */
private fun parseStored(text: String): RemoteRecipe = checkNotNull(
    RemoteRecipe.parse(text),
) { "Stored recipe is not a JSON object" }

internal fun SyncStatusEntity.problemOrNull(): SyncProblem? = when (problem) {
    null -> null
    ProblemKind.OFFLINE -> SyncProblem.Offline
    ProblemKind.SERVER_ERROR -> SyncProblem.ServerError(problemDetail?.toIntOrNull() ?: 0)
    ProblemKind.CREDENTIALS_REJECTED -> SyncProblem.CredentialsRejected
    ProblemKind.COOKBOOK_MISSING -> SyncProblem.CookbookMissing
    ProblemKind.COOKBOOK_TOO_OLD -> SyncProblem.CookbookTooOld(problemDetail.orEmpty())
}

internal fun SyncStatusEntity.withProblem(problem: SyncProblem): SyncStatusEntity = when (problem) {
    SyncProblem.Offline -> copy(problem = ProblemKind.OFFLINE, problemDetail = null)

    is SyncProblem.ServerError -> copy(
        problem = ProblemKind.SERVER_ERROR,
        problemDetail = problem.statusCode.toString(),
    )

    SyncProblem.CredentialsRejected ->
        copy(problem = ProblemKind.CREDENTIALS_REJECTED, problemDetail = null, credentialsRejected = true)

    SyncProblem.CookbookMissing -> copy(problem = ProblemKind.COOKBOOK_MISSING, problemDetail = null)

    is SyncProblem.CookbookTooOld -> copy(problem = ProblemKind.COOKBOOK_TOO_OLD, problemDetail = problem.version)
}
