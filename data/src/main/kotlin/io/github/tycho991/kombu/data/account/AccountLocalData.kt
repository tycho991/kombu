package io.github.tycho991.kombu.data.account

/**
 * The account's local data as the sign-in logic needs it;
 * [io.github.tycho991.kombu.data.recipes.RecipeRepository] implements it.
 */
interface AccountLocalData {
    /** Local changes a logout would lose: pending changes and conflicts. */
    suspend fun unsyncedCount(): Int

    /** Deletes the local recipe copy with its images and sync state. */
    suspend fun clearAll()

    /** Lets the sync run again after the user signed in again to the same account. */
    suspend fun credentialsAccepted()
}
