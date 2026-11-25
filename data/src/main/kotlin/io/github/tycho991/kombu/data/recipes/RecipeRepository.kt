package io.github.tycho991.kombu.data.recipes

import androidx.room.withTransaction
import io.github.tycho991.kombu.cookbookclient.ImageSize
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.account.AccountLocalData
import io.github.tycho991.kombu.data.recipes.db.KombuDatabase
import io.github.tycho991.kombu.data.recipes.db.SyncStatusEntity
import io.github.tycho991.kombu.data.recipes.db.bumpLocalChangeSeq
import io.github.tycho991.kombu.data.recipes.db.problemOrNull
import io.github.tycho991.kombu.data.recipes.db.toEntity
import io.github.tycho991.kombu.data.recipes.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/** The user's choice for a recipe in conflict (spec "Resolving a conflict"). */
enum class Resolution {
    /** Keep the local version (or the local deletion) and upload it. */
    KEEP_MINE,

    /** Take the server version (or the server's deletion) and drop the local one. */
    TAKE_SERVER,
}

/**
 * The local recipe copy as the app uses it: lists, status, offline edits and conflict resolution.
 *
 * Every change is stored at once and marked pending; the sync uploads it later (spec "Offline editing"). Each
 * change also advances the local change counter, so a running sync uploads it in one more pass (design D5).
 *
 * @param clock the current time in epoch milliseconds.
 */
class RecipeRepository(
    private val database: KombuDatabase,
    private val images: ImageStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : AccountLocalData {
    private val recipes = database.recipeDao()
    private val status = database.syncStatusDao()
    private val edits = LocalEdits(clock)

    /** Every recipe the app lists, by name; recipes deleted here are left out. */
    fun recipes(): Flow<List<LocalRecipe>> = recipes.visible().map { rows -> rows.map { it.toModel() } }

    /** The recipe with [localId], or `null` when it does not exist. */
    fun recipe(localId: Long): Flow<LocalRecipe?> = recipes.observe(localId).map { it?.toModel() }

    /** The stored image of the recipe with [localId] in [size], or `null`. */
    fun imageFile(localId: Long, size: ImageSize): File? = images.file(localId, size)

    /** Counts, last sync and current problem (design D12); whether a sync runs comes from the scheduler. */
    fun status(): Flow<SyncStatus> = combine(
        recipes.countVisible(),
        recipes.countPending(),
        recipes.countConflicts(),
        status.observe(),
    ) { count, pending, conflicts, row ->
        SyncStatus(
            recipeCount = count,
            pendingCount = pending,
            conflictCount = conflicts,
            lastSuccessAt = row?.lastSuccessAt,
            problem = row?.problemOrNull(),
        )
    }

    /** Local changes a logout would lose: pending changes and conflicts. */
    override suspend fun unsyncedCount(): Int = recipes.countPending().first() + recipes.countConflicts().first()

    /** Stores [recipe] as a new local recipe and returns its local id. An `id` field is dropped. */
    suspend fun create(recipe: RemoteRecipe): Long = database.withTransaction {
        status.bumpLocalChangeSeq()
        recipes.insert(
            LocalRecipe(local = recipe.without("id"), state = RecipeState.CREATED, localChangeAt = clock()).toEntity(),
        )
    }

    /**
     * Applies [change] to the shown version of the recipe with [localId] and stores the result as its pending
     * change. Fields [change] does not touch keep their value and type. Returns `false` when the recipe cannot be
     * edited: it does not exist, is deleted here, or is a conflict with no local version.
     */
    suspend fun edit(localId: Long, change: (RemoteRecipe) -> RemoteRecipe): Boolean = database.withTransaction {
        val current = recipes.byLocalId(localId)?.toModel()
        val updated = current?.let { edits.edited(it, change) }
        if (updated != null && updated != current) {
            recipes.update(updated.toEntity())
            status.bumpLocalChangeSeq()
        }
        updated != null
    }

    /**
     * Deletes the recipe with [localId] here; the sync deletes it on the server. A recipe that was never uploaded
     * is removed at once. Returns `false` for a missing recipe or one in conflict, which must be resolved first.
     */
    suspend fun delete(localId: Long): Boolean {
        var removed = false
        val deleted =
            database.withTransaction {
                val current = recipes.byLocalId(localId)?.toModel()
                when (current?.state) {
                    null, RecipeState.CONFLICT -> false

                    RecipeState.DELETED -> true

                    RecipeState.CREATED -> {
                        recipes.delete(localId)
                        removed = true
                        status.bumpLocalChangeSeq()
                        true
                    }

                    RecipeState.SYNCED, RecipeState.CHANGED -> {
                        recipes.update(edits.deleted(current).toEntity())
                        status.bumpLocalChangeSeq()
                        true
                    }
                }
            }
        if (removed) images.delete(localId)
        return deleted
    }

    /**
     * Resolves the conflict of [seen], the recipe as the user saw it, with [choice] (design D7). Returns `false`
     * and changes nothing when the stored recipe no longer equals [seen], for example because a sync brought a
     * newer server version in the meantime; the app then shows the conflict again.
     */
    suspend fun resolve(seen: LocalRecipe, choice: Resolution): Boolean {
        val resolved = edits.resolved(seen, choice)
        var removed = false
        val applied =
            database.withTransaction {
                val matches = seen.state == RecipeState.CONFLICT && recipes.byLocalId(seen.localId)?.toModel() == seen
                if (matches) {
                    if (resolved == null) recipes.delete(seen.localId) else recipes.update(resolved.toEntity())
                    removed = resolved == null || resolved.imageState == ImageState.NONE
                    status.bumpLocalChangeSeq()
                }
                matches
            }
        if (applied && removed) images.delete(seen.localId)
        return applied
    }

    /** Deletes the whole local copy: every recipe, every image and the sync state (design D13). */
    override suspend fun clearAll() {
        database.withTransaction {
            recipes.deleteAll()
            status.deleteAll()
        }
        images.deleteAll()
    }

    /** Allows syncing again after the user signed in again with new credentials. */
    override suspend fun credentialsAccepted() {
        database.withTransaction {
            val current = status.get() ?: SyncStatusEntity()
            status.put(current.copy(credentialsRejected = false, problem = null, problemDetail = null))
        }
    }
}
