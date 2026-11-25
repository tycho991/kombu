package io.github.tycho991.kombu.data.recipes

import androidx.room.withTransaction
import io.github.tycho991.kombu.cookbookclient.ImageSize
import io.github.tycho991.kombu.data.recipes.db.KombuDatabase
import io.github.tycho991.kombu.data.recipes.db.SyncStatusEntity
import io.github.tycho991.kombu.data.recipes.db.toEntity
import io.github.tycho991.kombu.data.recipes.db.toModel
import io.github.tycho991.kombu.data.recipes.db.withProblem

/** [LocalRecipeStore] on top of the Room database and the [ImageStore]. */
class RoomRecipeStore(private val database: KombuDatabase, private val images: ImageStore) : LocalRecipeStore {
    private val recipes = database.recipeDao()
    private val status = database.syncStatusDao()

    override suspend fun recipes(): List<LocalRecipe> = recipes.all().map { it.toModel() }

    override suspend fun insert(recipe: LocalRecipe): LocalRecipe {
        val localId = recipes.insert(recipe.copy(localId = 0).toEntity())
        return recipe.copy(localId = localId)
    }

    override suspend fun replace(expected: LocalRecipe, updated: LocalRecipe): Boolean = database.withTransaction {
        val matches = recipes.byLocalId(expected.localId)?.toModel() == expected
        if (matches) recipes.update(updated.copy(localId = expected.localId).toEntity())
        matches
    }

    override suspend fun remove(expected: LocalRecipe): Boolean {
        val removed =
            database.withTransaction {
                val matches = recipes.byLocalId(expected.localId)?.toModel() == expected
                if (matches) recipes.delete(expected.localId)
                matches
            }
        if (removed) images.delete(expected.localId)
        return removed
    }

    override suspend fun saveImages(localId: Long, full: ByteArray, thumb: ByteArray) {
        images.write(localId, ImageSize.FULL, full)
        images.write(localId, ImageSize.THUMB, thumb)
    }

    override suspend fun deleteImages(localId: Long) {
        images.delete(localId)
    }

    override suspend fun syncState(): SyncState {
        val current = status.get() ?: SyncStatusEntity()
        return SyncState(credentialsRejected = current.credentialsRejected, localChangeSeq = current.localChangeSeq)
    }

    override suspend fun recordSuccess(at: Long) {
        updateStatus { it.copy(lastSuccessAt = at, problem = null, problemDetail = null) }
    }

    override suspend fun recordProblem(problem: SyncProblem) {
        updateStatus { it.withProblem(problem) }
    }

    private suspend fun updateStatus(change: (SyncStatusEntity) -> SyncStatusEntity) {
        database.withTransaction { status.put(change(status.get() ?: SyncStatusEntity())) }
    }
}
