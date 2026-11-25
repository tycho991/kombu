package io.github.tycho991.kombu.sync

import io.github.tycho991.kombu.cookbookclient.ImageSize
import io.github.tycho991.kombu.data.recipes.ImageState
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.SyncProblem
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult

/**
 * Downloads the images of every recipe whose image is missing (design D8). A failed download leaves the recipe
 * as it is, marked missing, so the next run tries again (spec "Recipe images"). Only errors that affect every
 * request, such as lost network or rejected credentials, stop the run.
 */
internal class ImageStep(private val session: SyncSession) {
    private val store = session.store

    suspend fun run() {
        store
            .recipes()
            .filter { it.imageState == ImageState.MISSING && it.remoteId != null }
            .forEach { download(it) }
    }

    private suspend fun download(recipe: LocalRecipe) {
        val remoteId = checkNotNull(recipe.remoteId)
        val full = fetch(remoteId, ImageSize.FULL) ?: return
        val thumb = fetch(remoteId, ImageSize.THUMB) ?: return
        val stored = full.value != null && thumb.value != null
        if (stored) store.saveImages(recipe.localId, checkNotNull(full.value), checkNotNull(thumb.value))
        val updated = recipe.copy(imageState = if (stored) ImageState.PRESENT else ImageState.NONE)
        if (store.replace(recipe, updated) && !stored) store.deleteImages(recipe.localId)
    }

    /** The image, `Success(null)` for none, or `null` when this download failed but the run can go on. */
    private suspend fun fetch(remoteId: String, size: ImageSize): NextcloudResult.Success<ByteArray?>? =
        when (val result = session.client.downloadImage(session.credentials, remoteId, size)) {
            is NextcloudResult.Success -> result

            is NextcloudResult.Failure ->
                when (val problem = result.error.toProblem()) {
                    SyncProblem.Offline, SyncProblem.CredentialsRejected -> throw SyncAbort(problem)
                    else -> null
                }
        }
}
