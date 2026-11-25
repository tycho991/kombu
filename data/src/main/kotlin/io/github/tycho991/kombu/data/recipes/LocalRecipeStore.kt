package io.github.tycho991.kombu.data.recipes

/**
 * The local copy as the sync engine sees it (design D1). [RoomRecipeStore] implements it with Room; engine tests
 * use an in-memory implementation.
 *
 * The engine reads a recipe, talks to the server and then writes. The user may change the recipe in between, so
 * every write names the version it expects ([replace], [remove]) and does nothing when the stored row differs. The
 * engine leaves such a recipe to the next run instead of overwriting a newer local change.
 */
interface LocalRecipeStore {
    /** Every stored recipe, in any state. */
    suspend fun recipes(): List<LocalRecipe>

    /** Stores a new [recipe] and returns it with its assigned local id. */
    suspend fun insert(recipe: LocalRecipe): LocalRecipe

    /**
     * Replaces the stored recipe with [updated], in one transaction, if it still equals [expected]. Returns
     * whether it was replaced. [updated] keeps the local id of [expected].
     */
    suspend fun replace(expected: LocalRecipe, updated: LocalRecipe): Boolean

    /** Removes the stored recipe and its images if it still equals [expected]; returns whether it was removed. */
    suspend fun remove(expected: LocalRecipe): Boolean

    /** Stores both image sizes of the recipe with [localId], replacing older ones. */
    suspend fun saveImages(localId: Long, full: ByteArray, thumb: ByteArray)

    /** Deletes the images of the recipe with [localId]. */
    suspend fun deleteImages(localId: Long)

    /** The sync bookkeeping. */
    suspend fun syncState(): SyncState

    /** Records a completed sync at [at] (epoch milliseconds) and clears the problem. */
    suspend fun recordSuccess(at: Long)

    /** Records why a sync stopped; [SyncProblem.CredentialsRejected] also blocks further syncs. */
    suspend fun recordProblem(problem: SyncProblem)
}
