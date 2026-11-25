package io.github.tycho991.kombu.sync

import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.LocalRecipeStore
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.SyncProblem
import io.github.tycho991.kombu.data.recipes.SyncState

/**
 * [LocalRecipeStore] in memory, with the compare-and-set rules of the Room implementation. Its local edits follow
 * `RecipeRepository` for the cases the engine tests need.
 */
class InMemoryRecipeStore : LocalRecipeStore {
    private val rows = linkedMapOf<Long, LocalRecipe>()
    private var nextId = 1L
    private var state = SyncState()

    /** Stored images by local id: full and thumbnail. */
    val images = mutableMapOf<Long, Pair<ByteArray, ByteArray>>()

    /** The last recorded problem, `null` after a success. */
    var problem: SyncProblem? = null

    /** When the last sync succeeded. */
    var lastSuccessAt: Long? = null

    /** Called before every write; tests use it to change a recipe while the engine works. */
    var beforeWrite: suspend () -> Unit = {}

    val all: List<LocalRecipe> get() = rows.values.toList()

    fun byRemoteId(remoteId: String): LocalRecipe? = rows.values.firstOrNull { it.remoteId == remoteId }

    fun byName(name: String): LocalRecipe? = rows.values.firstOrNull { it.shown?.name == name }

    override suspend fun recipes(): List<LocalRecipe> = all

    override suspend fun insert(recipe: LocalRecipe): LocalRecipe {
        beforeWrite()
        check(recipe.remoteId == null || byRemoteId(recipe.remoteId!!) == null) { "remoteId must be unique" }
        val stored = recipe.copy(localId = nextId++)
        rows[stored.localId] = stored
        return stored
    }

    override suspend fun replace(expected: LocalRecipe, updated: LocalRecipe): Boolean {
        beforeWrite()
        if (rows[expected.localId] != expected) return false
        rows[expected.localId] = updated.copy(localId = expected.localId)
        return true
    }

    override suspend fun remove(expected: LocalRecipe): Boolean {
        beforeWrite()
        if (rows[expected.localId] != expected) return false
        rows.remove(expected.localId)
        images.remove(expected.localId)
        return true
    }

    override suspend fun saveImages(localId: Long, full: ByteArray, thumb: ByteArray) {
        images[localId] = full to thumb
    }

    override suspend fun deleteImages(localId: Long) {
        images.remove(localId)
    }

    override suspend fun syncState(): SyncState = state

    override suspend fun recordSuccess(at: Long) {
        lastSuccessAt = at
        problem = null
    }

    override suspend fun recordProblem(problem: SyncProblem) {
        this.problem = problem
        if (problem == SyncProblem.CredentialsRejected) state = state.copy(credentialsRejected = true)
    }

    // Local edits, as RecipeRepository makes them

    fun create(recipe: RemoteRecipe, at: Long = 0): LocalRecipe {
        val stored = LocalRecipe(localId = nextId++, local = recipe, state = RecipeState.CREATED, localChangeAt = at)
        rows[stored.localId] = stored
        bump()
        return stored
    }

    fun edit(localId: Long, at: Long = 0, change: (RemoteRecipe) -> RemoteRecipe): LocalRecipe {
        val current = rows.getValue(localId)
        val updated =
            when (current.state) {
                RecipeState.SYNCED, RecipeState.CHANGED ->
                    current.copy(
                        local = change(current.local ?: checkNotNull(current.base)),
                        state = RecipeState.CHANGED,
                    )

                else -> current.copy(local = change(checkNotNull(current.local)))
            }.copy(localChangeAt = at, uploadError = null)
        rows[localId] = updated
        bump()
        return updated
    }

    fun delete(localId: Long, at: Long = 0) {
        val current = rows.getValue(localId)
        if (current.state == RecipeState.CREATED) {
            rows.remove(localId)
        } else {
            rows[localId] = current.copy(local = null, state = RecipeState.DELETED, localChangeAt = at)
        }
        bump()
    }

    fun put(recipe: LocalRecipe) {
        rows[recipe.localId] = recipe
    }

    private fun bump() {
        state = state.copy(localChangeSeq = state.localChangeSeq + 1)
    }
}
