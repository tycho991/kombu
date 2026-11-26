package io.github.tycho991.kombu.ui.recipes.conflict

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tycho991.kombu.cookbookclient.RecipeDiff
import io.github.tycho991.kombu.cookbookclient.RecipeField
import io.github.tycho991.kombu.cookbookclient.RecipeFields
import io.github.tycho991.kombu.cookbookclient.RecipeValues
import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeRepository
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.Resolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A choice the user is asked to confirm, with the recipe exactly as it was shown when they chose. */
data class PendingChoice(val seen: LocalRecipe, val resolution: Resolution)

/** The state of the conflict view. */
sealed interface ConflictUiState {
    /** The recipe is being read. */
    data object Loading : ConflictUiState

    /** The recipe is no longer in conflict, or gone; the view closes. */
    data object Resolved : ConflictUiState

    /**
     * The conflict of [recipe]. For [ConflictKind.BOTH_CHANGED], [fields] lists the differing shown fields with
     * [mine] and [theirs]; for a deletion conflict, [mine] or [theirs] is the one version that exists.
     */
    data class Shown(
        val recipe: LocalRecipe,
        val kind: ConflictKind,
        val mine: RecipeValues?,
        val theirs: RecipeValues?,
        val fields: List<RecipeField> = emptyList(),
        val otherFields: Int = 0,
        val pending: PendingChoice? = null,
        /** The last choice was not applied, because the conflict changed while the view was open. */
        val changedMeanwhile: Boolean = false,
    ) : ConflictUiState
}

/**
 * The conflict view of the recipe with [localId] (design D6 of recipe-browse-edit). Choices go through
 * [RecipeRepository.resolve] with the recipe as the user saw it, which refuses when a sync changed it meanwhile.
 */
class ConflictViewModel(private val localId: Long, private val recipes: RecipeRepository) : ViewModel() {
    private val pending = MutableStateFlow<PendingChoice?>(null)
    private val changedMeanwhile = MutableStateFlow(false)

    /** The state of the screen. */
    val state: StateFlow<ConflictUiState> =
        combine(recipes.recipe(localId), pending, changedMeanwhile) { row, choice, changed ->
            val kind = row?.conflict
            if (row == null || row.state != RecipeState.CONFLICT || kind == null) {
                ConflictUiState.Resolved
            } else {
                shown(row, kind).copy(pending = choice, changedMeanwhile = changed)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ConflictUiState.Loading)

    /** Asks to confirm [resolution] for the conflict as it is shown now. */
    fun choose(resolution: Resolution) {
        val shown = state.value as? ConflictUiState.Shown ?: return
        changedMeanwhile.value = false
        pending.value = PendingChoice(shown.recipe, resolution)
    }

    /** Withdraws the choice. */
    fun cancel() {
        pending.value = null
    }

    /** Applies the chosen resolution; if the conflict changed meanwhile, nothing is applied and the view says so. */
    fun confirm() {
        val choice = pending.value ?: return
        pending.value = null
        viewModelScope.launch {
            changedMeanwhile.value = !recipes.resolve(choice.seen, choice.resolution)
        }
    }

    private fun shown(row: LocalRecipe, kind: ConflictKind): ConflictUiState.Shown {
        val mine = row.local?.let(RecipeFields::read)
        val theirs = row.server?.let(RecipeFields::read)
        val local = row.local
        val server = row.server
        val diff = if (local != null && server != null) RecipeDiff.between(local, server) else null
        return ConflictUiState.Shown(
            recipe = row,
            kind = kind,
            mine = mine,
            theirs = theirs,
            fields = diff?.fields.orEmpty(),
            otherFields = diff?.otherKeys ?: 0,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
