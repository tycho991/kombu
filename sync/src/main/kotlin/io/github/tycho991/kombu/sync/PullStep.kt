package io.github.tycho991.kombu.sync

import io.github.tycho991.kombu.cookbookclient.RecipeHash
import io.github.tycho991.kombu.cookbookclient.RecipeStub
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.data.recipes.ImageState
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeState

/**
 * Brings server changes into the local copy (design D5, step 2). Rows with a pending local change are left to
 * [PushStep], except when the server deleted their recipe. Every write is a compare-and-set, so a recipe the user
 * changes meanwhile is left alone until the next run.
 */
internal class PullStep(private val session: SyncSession) {
    private val client = session.client
    private val store = session.store

    /** Runs the pull and returns the server's recipe list by id, for the push. */
    suspend fun run(): Map<String, RecipeStub> {
        val stubs = client.listRecipes(session.credentials).orAbort().associateBy { it.id }
        val local = store.recipes().filter { it.remoteId != null }.associateBy { checkNotNull(it.remoteId) }
        stubs.values.forEach { stub -> pull(stub, local[stub.id]) }
        local.values.filter { it.remoteId !in stubs }.forEach { deletedOnServer(it) }
        return stubs
    }

    private suspend fun pull(stub: RecipeStub, local: LocalRecipe?) {
        when {
            local == null -> download(stub)?.let { store.insert(syncedFrom(stub, it)) }

            local.state == RecipeState.SYNCED && stub.changedSince(local.baseModified) ->
                download(stub)?.let { updateSynced(local, stub, it) }

            local.state == RecipeState.CONFLICT && (local.server == null || stub.changedSince(local.serverModified)) ->
                download(stub)?.let { updateConflict(local, stub, it) }

            // CHANGED and DELETED rows are checked by the push, right before it writes.
            else -> Unit
        }
    }

    private suspend fun download(stub: RecipeStub): RemoteRecipe? =
        client.getRecipe(session.credentials, stub.id).orNullIfMissing()

    private suspend fun updateSynced(local: LocalRecipe, stub: RecipeStub, server: RemoteRecipe) {
        if (RecipeHash.of(server) == local.baseHash) {
            store.replace(local, local.copy(baseModified = stub.dateModified))
            return
        }
        val updated = local.copy(base = server, baseModified = stub.dateModified, imageState = imageStateOf(server))
        if (store.replace(local, updated) && updated.imageState == ImageState.NONE) store.deleteImages(local.localId)
    }

    private suspend fun updateConflict(local: LocalRecipe, stub: RecipeStub, server: RemoteRecipe) {
        // A recipe deleted on the server may come back, for example from the trash; then both versions exist again.
        val returned = local.conflict == ConflictKind.DELETED_ON_SERVER
        val conflict = if (returned) ConflictKind.BOTH_CHANGED else local.conflict
        store.replace(local, local.copy(conflict = conflict, server = server, serverModified = stub.dateModified))
    }

    private suspend fun deletedOnServer(local: LocalRecipe) {
        when (local.state) {
            RecipeState.SYNCED, RecipeState.DELETED -> store.remove(local)

            RecipeState.CHANGED ->
                store.replace(
                    local,
                    local.copy(
                        state = RecipeState.CONFLICT,
                        conflict = ConflictKind.DELETED_ON_SERVER,
                        server = null,
                        serverModified = null,
                    ),
                )

            RecipeState.CONFLICT ->
                when (local.conflict) {
                    // Deleted in both places: nothing is left to decide.
                    ConflictKind.CHANGED_ON_SERVER_DELETED_HERE -> store.remove(local)

                    ConflictKind.BOTH_CHANGED ->
                        store.replace(
                            local,
                            local.copy(conflict = ConflictKind.DELETED_ON_SERVER, server = null, serverModified = null),
                        )

                    ConflictKind.DELETED_ON_SERVER, null -> Unit
                }

            // A recipe created here has no server id yet.
            RecipeState.CREATED -> Unit
        }
    }

    private fun RecipeStub.changedSince(modified: String?): Boolean = dateModified == null || dateModified != modified

    private fun syncedFrom(stub: RecipeStub, server: RemoteRecipe) = LocalRecipe(
        remoteId = stub.id,
        base = server,
        baseModified = stub.dateModified,
        state = RecipeState.SYNCED,
        imageState = imageStateOf(server),
    )
}

/** A recipe with an image needs a download; one without has nothing to store. */
internal fun imageStateOf(recipe: RemoteRecipe): ImageState =
    if (recipe.image == null) ImageState.NONE else ImageState.MISSING
