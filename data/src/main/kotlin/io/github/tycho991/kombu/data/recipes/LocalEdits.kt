package io.github.tycho991.kombu.data.recipes

import io.github.tycho991.kombu.cookbookclient.RemoteRecipe

/**
 * The state changes of local edits and conflict resolutions (spec "Offline editing", design D7), without storage.
 * [RecipeRepository] applies them in transactions.
 *
 * @param clock the current time in epoch milliseconds.
 */
internal class LocalEdits(private val clock: () -> Long) {
    /** [current] after a deletion here; only for synced and changed recipes. */
    fun deleted(current: LocalRecipe): LocalRecipe = current.copy(
        local = null,
        state = RecipeState.DELETED,
    ).pendingNow()

    /** [current] after applying [change] to its shown version, or `null` when it cannot be edited. */
    fun edited(current: LocalRecipe, change: (RemoteRecipe) -> RemoteRecipe): LocalRecipe? = when (current.state) {
        RecipeState.SYNCED, RecipeState.CHANGED -> {
            val base = checkNotNull(current.base)
            val local = change(current.local ?: base)
            if (local == base) {
                current.copy(local = null, state = RecipeState.SYNCED, uploadError = null, localChangeAt = null)
            } else {
                current.copy(local = local, state = RecipeState.CHANGED).pendingNow()
            }
        }

        RecipeState.CREATED -> current.copy(local = change(checkNotNull(current.local))).pendingNow()

        RecipeState.CONFLICT -> current.local?.let { current.copy(local = change(it), localChangeAt = clock()) }

        RecipeState.DELETED -> null
    }

    /** The recipe after resolving its conflict, or `null` when it is to be removed. */
    fun resolved(seen: LocalRecipe, choice: Resolution): LocalRecipe? {
        val fromServer =
            seen.copy(
                base = seen.server,
                baseModified = seen.serverModified,
                conflict = null,
                server = null,
                serverModified = null,
                uploadError = null,
            )
        return when (seen.conflict) {
            ConflictKind.BOTH_CHANGED, ConflictKind.CHANGED_ON_SERVER_DELETED_HERE ->
                if (choice == Resolution.KEEP_MINE) {
                    val state = if (seen.local == null) RecipeState.DELETED else RecipeState.CHANGED
                    fromServer.copy(state = state).pendingNow()
                } else {
                    fromServer.copy(local = null, state = RecipeState.SYNCED, localChangeAt = null).withImageRefresh()
                }

            ConflictKind.DELETED_ON_SERVER ->
                if (choice == Resolution.KEEP_MINE) {
                    fromServer
                        .copy(remoteId = null, base = null, baseModified = null, state = RecipeState.CREATED)
                        .copy(imageState = ImageState.NONE)
                        .pendingNow()
                } else {
                    null
                }

            null -> seen
        }
    }

    private fun LocalRecipe.pendingNow(): LocalRecipe = copy(uploadError = null, localChangeAt = clock())

    /** Marks the image of the new base for download, or removes it when the base has none. */
    private fun LocalRecipe.withImageRefresh(): LocalRecipe =
        copy(imageState = if (base?.image == null) ImageState.NONE else ImageState.MISSING)
}
