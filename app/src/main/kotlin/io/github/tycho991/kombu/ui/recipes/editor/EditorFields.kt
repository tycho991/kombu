package io.github.tycho991.kombu.ui.recipes.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.tycho991.kombu.R

private val Spacing = 8.dp
private const val MIN_TEXT_LINES = 3

private typealias Change = ((EditorForm) -> EditorForm) -> Unit

/** Every editable field of the recipe (spec "Editable fields"); the image is not offered. */
@Composable
internal fun EditorFields(state: EditorUiState, onChange: Change) {
    val form = state.form
    val errors = state.errors
    OutlinedTextField(
        value = form.name,
        onValueChange = { text -> onChange { it.copy(name = text) } },
        label = { Text(stringResource(R.string.editor_name)) },
        singleLine = true,
        isError = FormError.NAME_REQUIRED in errors || FormError.NAME_TAKEN in errors,
        supportingText = errorText(
            when {
                FormError.NAME_REQUIRED in errors -> R.string.editor_error_name_required
                FormError.NAME_TAKEN in errors -> R.string.editor_error_name_taken
                else -> null
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.description,
        onValueChange = { text -> onChange { it.copy(description = text) } },
        label = { Text(stringResource(R.string.editor_description)) },
        minLines = MIN_TEXT_LINES,
        modifier = Modifier.fillMaxWidth(),
    )
    CategoryField(form.category, state.categories, onChange)
    KeywordField(form, state.keywords, onChange)
    OutlinedTextField(
        value = form.servings,
        onValueChange = { text -> onChange { it.copy(servings = text) } },
        label = { Text(stringResource(R.string.editor_servings)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = FormError.SERVINGS_INVALID in errors,
        supportingText = errorText(R.string.editor_error_servings.takeIf { FormError.SERVINGS_INVALID in errors }),
        modifier = Modifier.fillMaxWidth(),
    )
    TimeField(R.string.editor_prep_time, form.prepTime, FormError.PREP_TIME_INVALID in errors) { time ->
        onChange { it.copy(prepTime = time) }
    }
    TimeField(R.string.editor_cook_time, form.cookTime, FormError.COOK_TIME_INVALID in errors) { time ->
        onChange { it.copy(cookTime = time) }
    }
    TimeField(R.string.editor_total_time, form.totalTime, FormError.TOTAL_TIME_INVALID in errors) { time ->
        onChange { it.copy(totalTime = time) }
    }
    OutlinedTextField(
        value = form.ingredients,
        onValueChange = { text -> onChange { it.copy(ingredients = text) } },
        label = { Text(stringResource(R.string.editor_ingredients)) },
        minLines = MIN_TEXT_LINES,
        modifier = Modifier.fillMaxWidth(),
    )
    StepFields(form.steps, onChange)
}

@Composable
private fun errorText(message: Int?): (@Composable () -> Unit)? = message?.let { { Text(stringResource(it)) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(category: String, categories: List<String>, onChange: Change) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = categories.filter { it.contains(category.trim(), ignoreCase = true) && it != category }
    ExposedDropdownMenuBox(expanded = expanded && suggestions.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = category,
            onValueChange = { text ->
                expanded = true
                onChange { it.copy(category = text) }
            },
            label = { Text(stringResource(R.string.editor_category)) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && suggestions.isNotEmpty(), onDismissRequest = { expanded = false }) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        expanded = false
                        onChange { it.copy(category = suggestion) }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun KeywordField(form: EditorForm, known: List<String>, onChange: Change) {
    val typed = form.keywordInput.trim()
    val chosen = form.keywords.map { it.lowercase() }.toSet()
    val suggestions = if (typed.isEmpty()) {
        emptyList()
    } else {
        known.filter { it.contains(typed, ignoreCase = true) && it.lowercase() !in chosen }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing)) {
        if (form.keywords.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing)) {
                form.keywords.forEach { keyword ->
                    InputChip(
                        selected = false,
                        onClick = { onChange { it.copy(keywords = it.keywords - keyword) } },
                        label = { Text(keyword) },
                        trailingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                stringResource(R.string.editor_keyword_remove, keyword),
                            )
                        },
                    )
                }
            }
        }
        ExposedDropdownMenuBox(expanded = suggestions.isNotEmpty(), onExpandedChange = {}) {
            OutlinedTextField(
                value = form.keywordInput,
                onValueChange = { text -> onChange { it.typingKeyword(text) } },
                label = { Text(stringResource(R.string.editor_keywords)) },
                placeholder = { Text(stringResource(R.string.editor_keyword_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            )
            ExposedDropdownMenu(expanded = suggestions.isNotEmpty(), onDismissRequest = {}) {
                suggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = { onChange { it.addingKeyword(suggestion) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeField(label: Int, time: TimeInput, invalid: Boolean, onChange: (TimeInput) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing)) {
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
        if (time.raw != null && !time.entered) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.editor_time_raw, time.raw), modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(time.copy(raw = null)) }) {
                    Text(stringResource(R.string.editor_time_raw_clear))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing)) {
            NumberField(
                R.string.editor_hours,
                time.hours,
                invalid,
                Modifier.weight(1f),
            ) { onChange(time.copy(hours = it)) }
            NumberField(R.string.editor_minutes, time.minutes, invalid, Modifier.weight(1f)) {
                onChange(time.copy(minutes = it))
            }
        }
        if (invalid) Text(stringResource(R.string.editor_error_time), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun NumberField(label: Int, value: String, invalid: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun StepFields(steps: List<String>, onChange: Change) {
    Text(stringResource(R.string.editor_steps), style = MaterialTheme.typography.titleSmall)
    steps.forEachIndexed { index, step ->
        Column {
            OutlinedTextField(
                value = step,
                onValueChange = { text -> onChange { it.copy(steps = it.steps.replacedAt(index, text)) } },
                label = { Text(stringResource(R.string.editor_step, index + 1)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                IconButton(
                    onClick = { onChange { it.copy(steps = it.steps.moved(index, index - 1)) } },
                    enabled = index > 0,
                ) {
                    Icon(painterResource(R.drawable.ic_arrow_upward), stringResource(R.string.editor_step_up))
                }
                IconButton(
                    onClick = { onChange { it.copy(steps = it.steps.moved(index, index + 1)) } },
                    enabled = index < steps.lastIndex,
                ) {
                    Icon(painterResource(R.drawable.ic_arrow_downward), stringResource(R.string.editor_step_down))
                }
                IconButton(onClick = { onChange { it.copy(steps = it.steps.filterIndexed { i, _ -> i != index }) } }) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.editor_step_remove))
                }
            }
        }
    }
    OutlinedButton(onClick = { onChange { it.copy(steps = it.steps + "") } }) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
        Text(stringResource(R.string.editor_step_add))
    }
}

/** This list with the element at [index] replaced by [value]. */
internal fun <T> List<T>.replacedAt(index: Int, value: T): List<T> = mapIndexed { i, old ->
    if (i ==
        index
    ) {
        value
    } else {
        old
    }
}

/** This list with the element at [from] moved to [to]; out-of-range moves change nothing. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
