package io.github.tycho991.kombu.ui.recipes.detail

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tycho991.kombu.R

/**
 * The recipe view with its [viewModel] (spec recipe-browsing, "Recipe view"). [onClose] is called when the recipe
 * disappears, for example after it was deleted.
 */
@Composable
fun RecipeDetailRoute(
    viewModel: RecipeDetailViewModel,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onEdit: () -> Unit = {},
    onResolve: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state is RecipeDetailUiState.Gone) {
        if (state is RecipeDetailUiState.Gone) onClose()
    }
    RecipeDetailScreen(
        state = state,
        actions = RecipeDetailActions(
            onBack = onBack,
            onEdit = onEdit,
            onResolve = onResolve,
            onDelete = viewModel::delete,
            onServings = viewModel::setServings,
            onOpenSource = { openSource(context, it) },
        ),
    )
}

/** The recipe view: the recipe, its sync state, scaled servings, and edit and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    state: RecipeDetailUiState,
    modifier: Modifier = Modifier,
    actions: RecipeDetailActions = RecipeDetailActions(),
) {
    val shown = state as? RecipeDetailUiState.Shown
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.account_back))
                    }
                },
                actions = {
                    if (shown != null) {
                        val inConflict = shown.recipe.inConflict
                        IconButton(onClick = if (inConflict) actions.onResolve else actions.onEdit) {
                            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.detail_edit))
                        }
                        IconButton(onClick = { if (inConflict) actions.onResolve() else confirmingDelete = true }) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.detail_delete))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
            if (shown == null) CircularProgressIndicator() else RecipeContent(shown, actions)
        }
    }
    if (confirmingDelete && shown != null) {
        DeleteDialog(
            name = shown.recipe.values.name.orEmpty(),
            onConfirm = {
                confirmingDelete = false
                actions.onDelete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun DeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = { Text(stringResource(R.string.delete_confirm_text, name)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.delete_dismiss)) } },
    )
}

/** Opens the recipe's source link in a Custom Tab; without a browser nothing happens (design D10). */
private fun openSource(context: Context, url: String) {
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
    } catch (ignored: ActivityNotFoundException) {
        // No browser installed or enabled; the link is the only network action and Kombu's state is untouched.
    }
}
