package io.github.tycho991.kombu.ui.recipes.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tycho991.kombu.R
import io.github.tycho991.kombu.ui.recipes.label

private val ContentPadding = 16.dp
private val FieldSpacing = 12.dp

/** The editor with its [viewModel]; [onDone] is called when it was saved or left (spec recipe-editing). */
@Composable
fun RecipeEditorRoute(viewModel: RecipeEditorViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }
    RecipeEditorScreen(
        state = state,
        actions = EditorActions(
            onChange = viewModel::update,
            onSave = viewModel::save,
            onDiscard = viewModel::discard,
            onKeepMine = viewModel::keepMine,
            onTakeTheirs = viewModel::takeTheirs,
            onSaveAsNew = viewModel::saveAsNew,
        ),
    )
}

/** The callbacks of [RecipeEditorScreen]. */
data class EditorActions(
    val onChange: ((EditorForm) -> EditorForm) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onKeepMine: () -> Unit = {},
    val onTakeTheirs: () -> Unit = {},
    val onSaveAsNew: () -> Unit = {},
)

/** The editor's frame: title, save, the form, and its questions. Leaving with changes asks first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(state: EditorUiState, modifier: Modifier = Modifier, actions: EditorActions = EditorActions()) {
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = { if (state.hasChanges) confirmingDiscard = true else actions.onDiscard() }
    BackHandler(onBack = leave)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.editor_close))
                    }
                },
                actions = {
                    TextButton(onClick = actions.onSave, enabled = !state.loading) {
                        Text(stringResource(R.string.editor_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.loading) {
            Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(ContentPadding),
                verticalArrangement = Arrangement.spacedBy(FieldSpacing),
            ) {
                if (state.saveFailed) {
                    Text(stringResource(R.string.editor_save_failed), color = MaterialTheme.colorScheme.error)
                }
                EditorFields(state, actions.onChange)
            }
        }
    }
    if (confirmingDiscard) {
        Question(
            title = stringResource(R.string.discard_title),
            text = stringResource(R.string.discard_text),
            confirm = stringResource(R.string.discard_confirm) to {
                confirmingDiscard = false
                actions.onDiscard()
            },
            dismiss = stringResource(R.string.discard_dismiss) to { confirmingDiscard = false },
        )
    }
    EditorQuestions(state.dialog, actions)
}

@Composable
private fun EditorQuestions(dialog: EditorDialog?, actions: EditorActions) {
    when (dialog) {
        null -> Unit

        is EditorDialog.Collision -> Question(
            title = stringResource(R.string.collision_title),
            text = stringResource(
                R.string.collision_text,
                dialog.fields.sorted().map { it.label() }.joinToString(", "),
            ),
            confirm = stringResource(R.string.collision_keep_mine) to actions.onKeepMine,
            dismiss = stringResource(R.string.collision_take_theirs) to actions.onTakeTheirs,
        )

        EditorDialog.Gone -> Question(
            title = stringResource(R.string.gone_title),
            text = stringResource(R.string.gone_text),
            confirm = stringResource(R.string.gone_save_new) to actions.onSaveAsNew,
            dismiss = stringResource(R.string.discard_confirm) to actions.onDiscard,
        )
    }
}

@Composable
private fun Question(
    title: String,
    text: String,
    confirm: Pair<String, () -> Unit>,
    dismiss: Pair<String, () -> Unit>,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm.second) { Text(confirm.first) } },
        dismissButton = { TextButton(onClick = dismiss.second) { Text(dismiss.first) } },
    )
}
