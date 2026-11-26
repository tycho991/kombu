package io.github.tycho991.kombu.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The destinations of the signed-in part of the app (design D1 of recipe-browse-edit). The back stack is a list of
 * these keys; they are serialisable, so the stack survives configuration changes and process death.
 */
@Serializable
sealed interface Route : NavKey {
    /** The recipe list, the root of the back stack. */
    @Serializable
    data object RecipeList : Route

    /** The recipe view of the recipe with [localId]. */
    @Serializable
    data class RecipeDetail(val localId: Long) : Route

    /** The editor for the recipe with [localId], or for a new recipe when it is `null`. */
    @Serializable
    data class RecipeEditor(val localId: Long? = null) : Route

    /** The conflict view of the recipe with [localId]. */
    @Serializable
    data class Conflict(val localId: Long) : Route

    /** The full sync status with "sync now" and "sign in again". */
    @Serializable
    data object SyncStatus : Route

    /** The account screen with logout. */
    @Serializable
    data object Account : Route

    /** The login for new credentials after the server rejected the stored ones. */
    @Serializable
    data object SignInAgain : Route
}
