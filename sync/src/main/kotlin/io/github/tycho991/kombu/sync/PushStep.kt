package io.github.tycho991.kombu.sync

import io.github.tycho991.kombu.cookbookclient.RecipeHash
import io.github.tycho991.kombu.cookbookclient.RecipeStub
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.data.recipes.ImageState
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.UploadError
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult

/**
 * Uploads pending local changes (design D5, step 3). Before it changes or deletes a server recipe, it downloads
 * the server version and compares it with the last synced one; a difference becomes a conflict, never an
 * overwrite. A refused upload is recorded on the recipe and the push goes on with the next one.
 *
 * @param stubs the server's recipe list from the pull, by id.
 */
internal class PushStep(private val session: SyncSession, private val stubs: Map<String, RecipeStub>) {
    private val client = session.client
    private val store = session.store
    private val credentials = session.credentials

    suspend fun run() {
        store
            .recipes()
            .filter { it.isPending }
            .sortedBy { it.localChangeAt ?: 0 }
            .forEach { recipe ->
                when (recipe.state) {
                    RecipeState.CREATED -> create(recipe)
                    RecipeState.CHANGED -> change(recipe)
                    RecipeState.DELETED -> delete(recipe)
                    RecipeState.SYNCED, RecipeState.CONFLICT -> Unit
                }
            }
    }

    private suspend fun create(recipe: LocalRecipe) {
        val sent = checkNotNull(recipe.local)
        when (val created = client.createRecipe(credentials, sent)) {
            is NextcloudResult.Success -> {
                val server = client.getRecipe(credentials, created.value).orAbort()
                afterUpload(recipe, created.value, server, sent)
            }

            is NextcloudResult.Failure ->
                when (created.error) {
                    NextcloudError.NameTaken -> nameTaken(recipe, sent)
                    else -> refused(recipe, created.error)
                }
        }
    }

    /**
     * A create can reach the server while its answer is lost; the retry then finds the name taken (design D6).
     * If the server recipe with that name has the content Kombu sent, it is Kombu's own upload.
     */
    private suspend fun nameTaken(recipe: LocalRecipe, sent: RemoteRecipe) {
        val stub = stubs.values.firstOrNull { it.name == sent.name }
        val server = stub?.let { client.getRecipe(credentials, it.id).orNullIfMissing() }
        if (stub == null || server == null || createdHash(server) != createdHash(sent)) {
            refused(recipe, NextcloudError.NameTaken)
            return
        }
        val pulledCopy = store.recipes().firstOrNull { it.remoteId == stub.id }
        when {
            pulledCopy == null -> afterUpload(recipe, stub.id, server, sent)

            // The pull already stored the uploaded recipe as new; this row is the same recipe twice.
            pulledCopy.state == RecipeState.SYNCED -> store.remove(recipe)

            else -> refused(recipe, NextcloudError.NameTaken)
        }
    }

    private suspend fun change(recipe: LocalRecipe) {
        val remoteId = checkNotNull(recipe.remoteId)
        val local = checkNotNull(recipe.local)
        val server = client.getRecipe(credentials, remoteId).orNullIfMissing()
        when {
            server == null -> conflict(recipe, ConflictKind.DELETED_ON_SERVER, null)

            RecipeHash.of(server) == recipe.baseHash ->
                when (val updated = client.updateRecipe(credentials, remoteId, local)) {
                    is NextcloudResult.Success ->
                        afterUpload(recipe, remoteId, client.getRecipe(credentials, remoteId).orAbort(), local)

                    is NextcloudResult.Failure -> refused(recipe, updated.error)
                }

            // The same edit on both sides, or an update whose answer was lost (design D11): nothing to decide.
            RecipeHash.of(server) == RecipeHash.of(local) -> afterUpload(recipe, remoteId, server, local)

            else -> conflict(recipe, ConflictKind.BOTH_CHANGED, server)
        }
    }

    private suspend fun delete(recipe: LocalRecipe) {
        val remoteId = checkNotNull(recipe.remoteId)
        val server = client.getRecipe(credentials, remoteId).orNullIfMissing()
        when {
            server == null -> store.remove(recipe)

            RecipeHash.of(server) == recipe.baseHash ->
                when (val deleted = client.deleteRecipe(credentials, remoteId)) {
                    is NextcloudResult.Success -> store.remove(recipe)

                    is NextcloudResult.Failure ->
                        when (deleted.error) {
                            NextcloudError.NotFound -> store.remove(recipe)
                            else -> refused(recipe, deleted.error)
                        }
                }

            else -> conflict(recipe, ConflictKind.CHANGED_ON_SERVER_DELETED_HERE, server)
        }
    }

    private suspend fun conflict(recipe: LocalRecipe, kind: ConflictKind, server: RemoteRecipe?) {
        val remoteId = checkNotNull(recipe.remoteId)
        store.replace(
            recipe,
            recipe.copy(
                state = RecipeState.CONFLICT,
                conflict = kind,
                server = server,
                serverModified = server?.let { stubs[remoteId]?.dateModified },
                uploadError = null,
            ),
        )
    }

    /** Records why the server refused the recipe; errors that affect every request stop the run instead. */
    private suspend fun refused(recipe: LocalRecipe, error: NextcloudError) {
        val reason =
            when {
                error == NextcloudError.NameTaken -> UploadError.NAME_TAKEN
                error == NextcloudError.NameMissing -> UploadError.NAME_MISSING
                error is NextcloudError.ServerError && error.statusCode in CLIENT_ERRORS -> UploadError.REJECTED
                error == NextcloudError.NotFound -> UploadError.REJECTED
                else -> throw SyncAbort(error.toProblem())
            }
        store.replace(recipe, recipe.copy(uploadError = reason))
    }

    /**
     * Makes [server], the server's version after Kombu wrote [uploaded], the new base of the recipe. The user may
     * have changed the recipe during the upload; then the newer local version stays pending on top of it, and a
     * recipe deleted during its first upload is deleted on the server next.
     */
    private suspend fun afterUpload(sent: LocalRecipe, remoteId: String, server: RemoteRecipe, uploaded: RemoteRecipe) {
        var current: LocalRecipe? = sent
        repeat(MAX_ATTEMPTS) {
            val row = current
            if (row == null) {
                store.insert(
                    LocalRecipe(
                        remoteId = remoteId,
                        base = server,
                        state = RecipeState.DELETED,
                        localChangeAt = session.clock(),
                    ),
                )
                return
            }
            if (store.replace(row, rebased(row, remoteId, server, uploaded))) return
            current = store.recipes().firstOrNull { it.localId == sent.localId }
        }
    }

    private fun rebased(row: LocalRecipe, remoteId: String, server: RemoteRecipe, uploaded: RemoteRecipe): LocalRecipe {
        val base = row.copy(remoteId = remoteId, base = server, baseModified = null, uploadError = null)
        return when {
            row.state == RecipeState.DELETED -> base

            row.local == uploaded -> {
                val imageKept = row.imageState == ImageState.PRESENT && row.base?.image == server.image
                base.copy(
                    local = null,
                    state = RecipeState.SYNCED,
                    localChangeAt = null,
                    imageState = if (imageKept) ImageState.PRESENT else imageStateOf(server),
                )
            }

            else -> base.copy(state = RecipeState.CHANGED)
        }
    }

    /** The server sets `dateCreated` when it creates a recipe, so a comparison with what Kombu sent ignores it. */
    private fun createdHash(recipe: RemoteRecipe): String = RecipeHash.of(recipe.without("dateCreated"))

    private companion object {
        val CLIENT_ERRORS = 400..499
        const val MAX_ATTEMPTS = 3
    }
}
