package io.github.tycho991.kombu.ui.recipes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.tycho991.kombu.R
import io.github.tycho991.kombu.cookbookclient.RecipeField

/** The name of [this] field as the editor's questions and the conflict view use it. */
@Composable
fun RecipeField.label(): String = stringResource(
    when (this) {
        RecipeField.NAME -> R.string.field_name
        RecipeField.DESCRIPTION -> R.string.field_description
        RecipeField.CATEGORY -> R.string.field_category
        RecipeField.KEYWORDS -> R.string.field_keywords
        RecipeField.SERVINGS -> R.string.field_servings
        RecipeField.PREP_TIME -> R.string.field_prep_time
        RecipeField.COOK_TIME -> R.string.field_cook_time
        RecipeField.TOTAL_TIME -> R.string.field_total_time
        RecipeField.INGREDIENTS -> R.string.field_ingredients
        RecipeField.STEPS -> R.string.field_steps
    },
)
