package io.github.tycho991.kombu.ui.recipes.conflict

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tycho991.kombu.R
import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.data.recipes.Resolution

private val ContentPadding = 16.dp
private val Spacing = 12.dp

/** One choice of a conflict kind: what it is called and what its confirmation says it discards (design D6). */
private class Choice(val resolution: Resolution, val label: Int, val confirmation: Int)

/** The two choices of [kind], keep first; they map to [Resolution] exactly as design D7 of recipe-sync defines. */
private fun choices(kind: ConflictKind): List<Choice> = when (kind) {
    ConflictKind.BOTH_CHANGED -> listOf(
        Choice(Resolution.KEEP_MINE, R.string.conflict_keep_mine, R.string.conflict_confirm_keep_mine),
        Choice(Resolution.TAKE_SERVER, R.string.conflict_take_server, R.string.conflict_confirm_take_server),
    )

    ConflictKind.DELETED_ON_SERVER -> listOf(
        Choice(Resolution.KEEP_MINE, R.string.conflict_keep_recipe, R.string.conflict_confirm_keep_recipe),
        Choice(Resolution.TAKE_SERVER, R.string.conflict_delete_here, R.string.conflict_confirm_delete_here),
    )

    ConflictKind.CHANGED_ON_SERVER_DELETED_HERE -> listOf(
        Choice(Resolution.KEEP_MINE, R.string.conflict_delete_anyway, R.string.conflict_confirm_delete_anyway),
        Choice(Resolution.TAKE_SERVER, R.string.conflict_keep_server, R.string.conflict_confirm_keep_server),
    )
}

/** The conflict view with its [viewModel]; [onDone] is called when the conflict is resolved. */
@Composable
fun ConflictRoute(viewModel: ConflictViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state is ConflictUiState.Resolved) {
        if (state is ConflictUiState.Resolved) onDone()
    }
    ConflictScreen(
        state = state,
        onBack = onBack,
        onChoose = viewModel::choose,
        onConfirm = viewModel::confirm,
        onCancel = viewModel::cancel,
    )
}

/** The conflict view (spec "Conflict view", "Choosing a conflict version"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictScreen(
    state: ConflictUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onChoose: (Resolution) -> Unit = {},
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val shown = state as? ConflictUiState.Shown
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conflict_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.account_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (shown == null) {
            Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(ContentPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing),
            ) {
                if (shown.changedMeanwhile) {
                    Text(stringResource(R.string.conflict_changed_meanwhile), color = MaterialTheme.colorScheme.error)
                }
                ConflictDetails(shown)
                val (keep, take) = choices(shown.kind)
                Button(onClick = { onChoose(keep.resolution) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(keep.label))
                }
                OutlinedButton(onClick = { onChoose(take.resolution) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(take.label))
                }
            }
        }
    }
    val pending = shown?.pending
    if (pending != null) {
        val choice = choices(shown.kind).first { it.resolution == pending.resolution }
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.conflict_confirm_title)) },
            text = { Text(stringResource(choice.confirmation)) },
            confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(choice.label)) } },
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.delete_dismiss)) } },
        )
    }
}
