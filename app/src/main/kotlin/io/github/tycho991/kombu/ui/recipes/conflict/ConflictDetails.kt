package io.github.tycho991.kombu.ui.recipes.conflict

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.tycho991.kombu.R
import io.github.tycho991.kombu.cookbookclient.RecipeField
import io.github.tycho991.kombu.cookbookclient.RecipeTime
import io.github.tycho991.kombu.cookbookclient.RecipeValues
import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.ui.recipes.label
import io.github.tycho991.kombu.ui.recipes.recipeTimeText

private val CardPadding = 12.dp
private val LineSpacing = 4.dp

/** The explanation of the conflict and the versions: both values per differing field, or the one version left. */
@Composable
internal fun ConflictDetails(state: ConflictUiState.Shown) {
    Text(
        stringResource(
            when (state.kind) {
                ConflictKind.BOTH_CHANGED -> R.string.banner_conflict_both
                ConflictKind.DELETED_ON_SERVER -> R.string.banner_conflict_deleted_on_server
                ConflictKind.CHANGED_ON_SERVER_DELETED_HERE -> R.string.banner_conflict_deleted_here
            },
        ),
        style = MaterialTheme.typography.bodyLarge,
    )
    val mine = state.mine
    val theirs = state.theirs
    when {
        mine != null && theirs != null -> {
            state.fields.forEach { field -> FieldComparison(field, mine, theirs) }
            if (state.otherFields > 0) {
                Text(
                    pluralStringResource(R.plurals.conflict_other_fields, state.otherFields, state.otherFields),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        mine != null -> Version(stringResource(R.string.conflict_version_kombu), mine)

        theirs != null -> Version(stringResource(R.string.conflict_version_nextcloud), theirs)
    }
}

@Composable
private fun FieldComparison(field: RecipeField, mine: RecipeValues, theirs: RecipeValues) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
            Text(field.label(), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.conflict_in_kombu), style = MaterialTheme.typography.labelMedium)
            Text(valueText(field, mine), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.conflict_in_nextcloud), style = MaterialTheme.typography.labelMedium)
            Text(valueText(field, theirs), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The one version of a deletion conflict: every shown field it has. */
@Composable
private fun Version(title: String, values: RecipeValues) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            RecipeField.entries.filter { hasValue(values[it]) }.forEach { field ->
                Text(field.label(), style = MaterialTheme.typography.labelMedium)
                Text(valueText(field, values), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun hasValue(value: Any?): Boolean = value != null && value != emptyList<Any>()

/** The value of [field] in [values] as text; lists one entry per line, times in the units of the app language. */
@Composable
private fun valueText(field: RecipeField, values: RecipeValues): String = when (val value = values[field]) {
    null -> stringResource(R.string.conflict_empty)
    is RecipeTime -> recipeTimeText(value)
    is List<*> -> if (value.isEmpty()) stringResource(R.string.conflict_empty) else value.joinToString("\n")
    else -> value.toString()
}
