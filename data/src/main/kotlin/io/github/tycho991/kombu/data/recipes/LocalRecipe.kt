package io.github.tycho991.kombu.data.recipes

import io.github.tycho991.kombu.cookbookclient.RecipeHash
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe

/** Where a local recipe stands relative to the server (design D4). */
enum class RecipeState {
    /** Equal to the last synced server version; nothing to upload. */
    SYNCED,

    /** Created in Kombu and not uploaded yet. */
    CREATED,

    /** Changed in Kombu; the change is not uploaded yet. */
    CHANGED,

    /** Deleted in Kombu; the deletion is not uploaded yet. The row is hidden from lists. */
    DELETED,

    /** Changed in both places, or deleted in one and changed in the other; waits for the user's choice. */
    CONFLICT,
}

/** What kind of conflict a recipe in [RecipeState.CONFLICT] has. */
enum class ConflictKind {
    /** Changed here and on the server; both versions are kept. */
    BOTH_CHANGED,

    /** Changed here, but deleted on the server. */
    DELETED_ON_SERVER,

    /** Deleted here, but changed on the server. */
    CHANGED_ON_SERVER_DELETED_HERE,
}

/** Why the server refused the last upload of a recipe (spec "Rejected uploads keep the local change"). */
enum class UploadError {
    /** Another recipe on the server already has the name. */
    NAME_TAKEN,

    /** The recipe has no name. */
    NAME_MISSING,

    /** The server refused it for another reason. */
    REJECTED,
}

/** Whether the image files of a recipe are on the device. */
enum class ImageState {
    /** The recipe has no image. */
    NONE,

    /** Full image and thumbnail are stored. */
    PRESENT,

    /** The recipe has an image that is not stored yet; the next sync downloads it. */
    MISSING,
}

/**
 * One recipe in the local copy, with its sync state (design D4).
 *
 * @property localId stable local key; `0` for a recipe that is not stored yet.
 * @property remoteId the server's id, or `null` for a recipe that was never uploaded.
 * @property base the last synced server version, or `null` for a recipe created here.
 * @property baseModified the `dateModified` of [base] as the server's recipe list reports it.
 * @property local the pending local version, or `null` when there is none.
 * @property conflict the kind of conflict, only in [RecipeState.CONFLICT].
 * @property server the server version in a conflict; `null` when it was deleted there.
 * @property serverModified the recipe list's `dateModified` of [server].
 * @property uploadError why the server refused the last upload, or `null`.
 * @property localChangeAt when the pending change was made (epoch milliseconds), for upload order.
 */
data class LocalRecipe(
    val localId: Long = 0,
    val remoteId: String? = null,
    val base: RemoteRecipe? = null,
    val baseModified: String? = null,
    val local: RemoteRecipe? = null,
    val state: RecipeState,
    val conflict: ConflictKind? = null,
    val server: RemoteRecipe? = null,
    val serverModified: String? = null,
    val uploadError: UploadError? = null,
    val imageState: ImageState = ImageState.NONE,
    val localChangeAt: Long? = null,
) {
    /** The version the app shows: the local one if there is one, otherwise the synced or the server version. */
    val shown: RemoteRecipe? get() = local ?: base ?: server

    /** The content hash of [base], or `null` for a recipe created here. */
    val baseHash: String? get() = base?.let(RecipeHash::of)

    /** Whether this recipe has a change that is not on the server yet. */
    val isPending: Boolean get() = state in PENDING_STATES

    internal companion object {
        val PENDING_STATES = setOf(RecipeState.CREATED, RecipeState.CHANGED, RecipeState.DELETED)
    }
}
