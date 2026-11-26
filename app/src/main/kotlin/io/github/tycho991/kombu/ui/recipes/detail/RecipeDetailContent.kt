package io.github.tycho991.kombu.ui.recipes.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.tycho991.kombu.R
import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.data.recipes.UploadError
import io.github.tycho991.kombu.ui.recipes.RecipeImage
import io.github.tycho991.kombu.ui.recipes.recipeTimeText

private val ContentPadding = 16.dp
private val SectionSpacing = 16.dp
private val LineSpacing = 4.dp
private const val IMAGE_ASPECT_RATIO = 16f / 9f

/** The recipe's fields; a field the recipe does not have is left out without a placeholder. */
@Composable
internal fun RecipeContent(state: RecipeDetailUiState.Shown, actions: RecipeDetailActions) {
    val recipe = state.recipe
    val values = recipe.values
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (recipe.image != null) {
            RecipeImage(recipe.image, recipe.imageVersion, Modifier.fillMaxWidth().aspectRatio(IMAGE_ASPECT_RATIO))
        }
        Column(
            modifier = Modifier.padding(ContentPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
                Text(values.name.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                values.category?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                if (values.keywords.isNotEmpty()) {
                    Text(values.keywords.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
            recipe.banner?.let { Banner(it, actions) }
            values.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Times(state)
            state.servings?.let { Servings(it, actions.onServings) }
            if (state.ingredients.isNotEmpty()) Ingredients(state.ingredients)
            if (values.steps.isNotEmpty()) Steps(values.steps)
            values.sourceUrl?.let { url ->
                TextButton(onClick = { actions.onOpenSource(url) }) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null)
                    Text(stringResource(R.string.detail_source), modifier = Modifier.padding(start = LineSpacing))
                }
            }
        }
    }
}

@Composable
private fun Banner(banner: RecipeBanner, actions: RecipeDetailActions) {
    val (text, action) = when (banner) {
        RecipeBanner.Pending -> stringResource(R.string.banner_pending) to null

        is RecipeBanner.Conflict -> stringResource(
            when (banner.kind) {
                ConflictKind.BOTH_CHANGED -> R.string.banner_conflict_both
                ConflictKind.DELETED_ON_SERVER -> R.string.banner_conflict_deleted_on_server
                ConflictKind.CHANGED_ON_SERVER_DELETED_HERE -> R.string.banner_conflict_deleted_here
            },
        ) to (R.string.banner_resolve to actions.onResolve)

        is RecipeBanner.Refused -> when (banner.error) {
            UploadError.NAME_TAKEN ->
                stringResource(R.string.banner_refused_name_taken) to (R.string.banner_edit_name to actions.onEdit)

            UploadError.NAME_MISSING ->
                stringResource(R.string.banner_refused_name_missing) to (R.string.banner_edit_name to actions.onEdit)

            UploadError.REJECTED -> stringResource(
                R.string.banner_refused_other,
            ) to (R.string.detail_edit to actions.onEdit)
        }
    }
    val alarming = banner !is RecipeBanner.Pending
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (alarming) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(ContentPadding), verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(stringResource(label)) } }
        }
    }
}

@Composable
private fun Times(state: RecipeDetailUiState.Shown) {
    val values = state.recipe.values
    val times = listOfNotNull(
        values.prepTime?.let { stringResource(R.string.detail_prep_time, recipeTimeText(it)) },
        values.cookTime?.let { stringResource(R.string.detail_cook_time, recipeTimeText(it)) },
        values.totalTime?.let { stringResource(R.string.detail_total_time, recipeTimeText(it)) },
    )
    if (times.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
            times.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun Servings(servings: Int, onServings: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onServings(servings - 1) }, enabled = servings > 1) {
            Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.detail_servings_decrease))
        }
        Text(
            pluralStringResource(R.plurals.detail_servings, servings, servings),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { onServings(servings + 1) }) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.detail_servings_increase))
        }
    }
}

@Composable
private fun Ingredients(rows: List<IngredientRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
        Text(stringResource(R.string.detail_ingredients), style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            when {
                row.heading -> Text(row.text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                row.unscaled -> Row(horizontalArrangement = Arrangement.spacedBy(LineSpacing)) {
                    Text(row.text, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.detail_not_scaled),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> Text(row.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun Steps(steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
        Text(stringResource(R.string.detail_steps), style = MaterialTheme.typography.titleMedium)
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(LineSpacing)) {
                Text(
                    stringResource(R.string.detail_step_number, index + 1),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(step, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
