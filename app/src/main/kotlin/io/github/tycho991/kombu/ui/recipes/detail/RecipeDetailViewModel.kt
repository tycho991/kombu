package io.github.tycho991.kombu.ui.recipes.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tycho991.kombu.cookbookclient.ImageSize
import io.github.tycho991.kombu.cookbookclient.RecipeFields
import io.github.tycho991.kombu.cookbookclient.RecipeValues
import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeRepository
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.UploadError
import io.github.tycho991.kombu.ingredientparser.IngredientLine
import io.github.tycho991.kombu.ingredientparser.IngredientParser
import io.github.tycho991.kombu.ingredientparser.Rational
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** What the recipe view tells about the recipe's sync state (spec "Recipe state in the view"). */
sealed interface RecipeBanner {
    /** A change is not uploaded yet. */
    data object Pending : RecipeBanner

    /** The recipe is in conflict of [kind]; the view offers to resolve it. */
    data class Conflict(val kind: ConflictKind) : RecipeBanner

    /** The server refused the upload because of [error]; the view offers to edit the recipe. */
    data class Refused(val error: UploadError) : RecipeBanner
}

/** One ingredient line as shown: [unscaled] marks a line that could not be scaled while scaling is active. */
data class IngredientRow(val text: String, val heading: Boolean = false, val unscaled: Boolean = false)

/** The recipe as the view shows it. */
data class RecipeDetail(
    val localId: Long,
    val values: RecipeValues,
    val image: File? = null,
    val imageVersion: Long = 0,
    val banner: RecipeBanner? = null,
    /** Edit and delete route to the conflict view (spec "Recipes in conflict are resolved first"). */
    val inConflict: Boolean = false,
)

/** The state of the recipe view. */
sealed interface RecipeDetailUiState {
    /** The recipe is being read. */
    data object Loading : RecipeDetailUiState

    /** The recipe no longer exists or was deleted here; the view closes. */
    data object Gone : RecipeDetailUiState

    /** The recipe with the chosen [servings] and its [ingredients] scaled to them. */
    data class Shown(val recipe: RecipeDetail, val servings: Int?, val ingredients: List<IngredientRow>) :
        RecipeDetailUiState
}

/**
 * The recipe view of the recipe with [localId] (spec recipe-browsing, "Recipe view", "Scaled servings").
 * Scaling is a view: the chosen servings live in [saved] and never change the stored recipe.
 *
 * @param locale the app language, for scaled quantities.
 */
class RecipeDetailViewModel(
    private val localId: Long,
    private val recipes: RecipeRepository,
    private val saved: SavedStateHandle,
    private val locale: Locale,
    computation: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val loaded = recipes.recipe(
        localId,
    ).map { row -> row?.takeIf { it.state != RecipeState.DELETED }?.let(::detail) }
        .flowOn(computation)

    /** The state of the screen. */
    val state: StateFlow<RecipeDetailUiState> =
        combine(loaded, saved.getStateFlow<Int?>(SERVINGS, null)) { recipe, chosen ->
            if (recipe == null) {
                RecipeDetailUiState.Gone
            } else {
                val stored = recipe.values.servings
                val servings = if (stored == null) null else chosen ?: stored
                val factor = if (stored == null || servings == null) {
                    Rational.ONE
                } else {
                    Rational.of(
                        servings.toLong(),
                        stored.toLong(),
                    )
                }
                RecipeDetailUiState.Shown(recipe, servings, rows(recipe.values.ingredients, factor))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RecipeDetailUiState.Loading)

    /** Shows the ingredients for [servings], at least one. */
    fun setServings(servings: Int) {
        saved[SERVINGS] = servings.coerceAtLeast(1)
    }

    /** Deletes the recipe here; the next sync deletes it on the server (spec "Delete a recipe"). */
    fun delete() {
        viewModelScope.launch { recipes.delete(localId) }
    }

    private fun detail(row: LocalRecipe): RecipeDetail? {
        val shown = row.shown ?: return null
        val image = recipes.imageFile(row.localId, ImageSize.FULL)
        return RecipeDetail(
            localId = row.localId,
            values = RecipeFields.read(shown),
            image = image,
            imageVersion = image?.lastModified() ?: 0,
            banner = when {
                row.state == RecipeState.CONFLICT -> row.conflict?.let(RecipeBanner::Conflict)
                row.uploadError != null -> RecipeBanner.Refused(row.uploadError!!)
                row.isPending -> RecipeBanner.Pending
                else -> null
            },
            inConflict = row.state == RecipeState.CONFLICT,
        )
    }

    private fun rows(lines: List<String>, factor: Rational): List<IngredientRow> {
        val scaling = factor != Rational.ONE
        return lines.map { line ->
            when (val parsed = IngredientParser.parse(line)) {
                is IngredientLine.Heading -> IngredientRow(parsed.text, heading = true)

                is IngredientLine.Measured ->
                    IngredientRow(if (scaling) parsed.displayText(locale, factor) else parsed.original)

                is IngredientLine.Unmeasured -> IngredientRow(parsed.original, unscaled = scaling)
            }
        }
    }

    private companion object {
        const val SERVINGS = "servings"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
