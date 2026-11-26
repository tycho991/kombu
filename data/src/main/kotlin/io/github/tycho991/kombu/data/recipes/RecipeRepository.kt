package io.github.tycho991.kombu.data.recipes

import androidx.room.withTransaction
import io.github.tycho991.kombu.cookbookclient.ImageSize
import io.github.tycho991.kombu.cookbookclient.RecipeField
import io.github.tycho991.kombu.cookbookclient.RecipeFields
import io.github.tycho991.kombu.cookbookclient.RecipeValues
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.Locale

/** The user's choice for a recipe in conflict (spec "Resolving a conflict"). */
enum class Resolution {
    /** Keep the local version (or the local deletion) and upload it. */
    KEEP_MINE,

    /** Take the server version (or the server's deletion) and drop the local one. */
    TAKE_SERVER,
}

/** The fields an editor changed, with their new values (design D5 of recipe-browse-edit). */
data class RecipeChange(
    /** The edited fields; only these are written. */
    val fields: Set<RecipeField>,
    /** The values of the edited fields; values of other fields are ignored. */
    val values: RecipeValues,
)

/** The outcome of [RecipeRepository.edit]. */
sealed interface EditResult {
    /** The change is stored. Changes a sync made meanwhile to other fields are kept. */
    data object Edited : EditResult

    /**
     * Nothing is stored: a sync changed [fields], which the user also edited, since the editor opened. [current]
     * is the version now stored; editing it again with `expected = current` saves the user's values anyway.
     */
    data class Collision(val fields: Set<RecipeField>, val current: RemoteRecipe) : EditResult

    /** Nothing is stored: the recipe was removed or deleted meanwhile. */
    data object Gone : EditResult
}

/**
 * The local recipe copy as the app uses it: lists, status, offline edits and conflict resolution.
 *
 * Every change is stored at once and marked pending; the sync uploads it later (spec "Offline editing"). Each
 * change also advances the local change counter, so a running sync uploads it in one more pass (design D5).
 *
 * @param clock the current time in epoch milliseconds.
 */
// The repository is the one entry point to the local copy; each function is one use case of the screens or the
// sync, and splitting it would scatter the transactions that keep the copy consistent.
@Suppress("TooManyFunctions")
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

    /** The distinct categories of the listed recipes, sorted for [locale] (design D7). */
    fun categories(locale: Locale): Flow<List<String>> =
        recipes().map { RecipeQuery(locale).categories(it) }.distinctUntilChanged()

    /** The distinct keywords of the listed recipes, sorted for [locale] (design D7). */
    fun keywords(locale: Locale): Flow<List<String>> =
        recipes().map { RecipeQuery(locale).keywords(it) }.distinctUntilChanged()

    /**
     * Whether another listed recipe than [exceptLocalId] already has [name], compared trimmed and ignoring case
     * (spec "Recipe name"). Recipes deleted here do not count.
     */
    suspend fun isNameTaken(name: String, exceptLocalId: Long? = null): Boolean {
        val wanted = name.trim().lowercase(Locale.ROOT)
        return recipes.visible().first().any { row ->
            val model = row.toModel()
            model.localId != exceptLocalId && model.shown?.name?.trim()?.lowercase(Locale.ROOT) == wanted
        }
    }

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
            localChangeSeq = row?.localChangeSeq ?: 0,
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
     * Writes the fields of [change] into the recipe with [localId], if no sync changed them since the editor read
     * [expected] (design D5). In one transaction it compares the stored shown version with [expected]:
     * - unchanged, or changed only in fields [change] does not edit: the fields are written into the stored
     *   version, which keeps the sync's other changes, and the result is [EditResult.Edited];
     * - changed in an edited field: nothing is stored, and the result names those fields;
     * - removed, or deleted here: nothing is stored, and the result is [EditResult.Gone].
     *
     * A change that leaves the recipe as it is stores nothing and does not mark it as changed.
     */
    suspend fun edit(localId: Long, expected: RemoteRecipe, change: RecipeChange): EditResult =
        database.withTransaction {
            val row = recipes.byLocalId(localId)?.toModel()
            val current = row?.shown
            when {
                row == null || current == null || row.state == RecipeState.DELETED -> EditResult.Gone
                else -> editUnlessCollision(row, current, expected, change)
            }
        }

    /** Writes [change] into [row] unless a field it edits differs between [expected] and [current]. */
    private suspend fun editUnlessCollision(
        row: LocalRecipe,
        current: RemoteRecipe,
        expected: RemoteRecipe,
        change: RecipeChange,
    ): EditResult {
        val collisions = change.fields intersect RecipeFields.read(expected).differingFields(RecipeFields.read(current))
        if (collisions.isNotEmpty()) return EditResult.Collision(collisions, current)
        // Only a conflict without a local version cannot be edited; the editor never opens one (spec
        // "Recipes in conflict are resolved first").
        val updated = edits.edited(row) { RecipeFields.write(it, change.fields, change.values) }
        if (updated != null && updated != row) {
            recipes.update(updated.toEntity())
            status.bumpLocalChangeSeq()
        }
        return if (updated == null) EditResult.Gone else EditResult.Edited
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
