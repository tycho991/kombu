package io.github.tycho991.kombu.ui.recipes.detail

/** The callbacks of [RecipeDetailScreen]; edit and resolve lead to other screens. */
data class RecipeDetailActions(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onResolve: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onServings: (Int) -> Unit = {},
    val onOpenSource: (String) -> Unit = {},
)
