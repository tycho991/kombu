package io.github.tycho991.kombu.ui.recipes.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.tycho991.kombu.R

private val ChipPadding = 16.dp
private val ChipSpacing = 8.dp

/** The filter chips of the recipe list: attention, category and keywords (spec "Filters", "Attention filter"). */
@Composable
internal fun FilterRow(state: RecipeListUiState, actions: RecipeListActions) {
    val listState = state.listState
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = ChipPadding),
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
    ) {
        if (state.bar.attentionCount > 0 || listState.attentionOnly) {
            FilterChip(
                selected = listState.attentionOnly,
                onClick = { actions.onAttentionOnly(!listState.attentionOnly) },
                label = { Text(stringResource(R.string.recipes_filter_attention)) },
            )
        }
        if (state.categories.isNotEmpty() || listState.category != null) CategoryChip(state, actions.onCategory)
        if (state.keywords.isNotEmpty() || listState.keywords.isNotEmpty()) KeywordChip(state, actions.onToggleKeyword)
    }
}

@Composable
private fun CategoryChip(state: RecipeListUiState, onCategory: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = state.listState.category
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected ?: stringResource(R.string.recipes_filter_category)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.recipes_filter_category_all)) },
                onClick = {
                    open = false
                    onCategory(null)
                },
            )
            state.categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    leadingIcon = { RadioButton(selected = category == selected, onClick = null) },
                    onClick = {
                        open = false
                        onCategory(category)
                    },
                )
            }
        }
    }
}

@Composable
private fun KeywordChip(state: RecipeListUiState, onToggleKeyword: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = state.listState.keywords
    Box {
        FilterChip(
            selected = selected.isNotEmpty(),
            onClick = { open = true },
            label = {
                Text(
                    when (selected.size) {
                        0 -> stringResource(R.string.recipes_filter_keywords)

                        1 -> selected.single()

                        else -> pluralStringResource(
                            R.plurals.recipes_filter_keywords_selected,
                            selected.size,
                            selected.size,
                        )
                    },
                )
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (state.keywords + selected.filter { it !in state.keywords }).forEach { keyword ->
                DropdownMenuItem(
                    text = { Text(keyword) },
                    leadingIcon = { Checkbox(checked = keyword in selected, onCheckedChange = null) },
                    onClick = { onToggleKeyword(keyword) },
                )
            }
        }
    }
}
