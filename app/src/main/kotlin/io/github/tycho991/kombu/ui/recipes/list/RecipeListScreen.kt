package io.github.tycho991.kombu.ui.recipes.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tycho991.kombu.R
import io.github.tycho991.kombu.data.recipes.SortOrder
import io.github.tycho991.kombu.ui.recipes.RecipeImage
import io.github.tycho991.kombu.ui.start.message

private val ScreenPadding = 16.dp
private val ItemSpacing = 8.dp
private val ThumbnailSize = 56.dp
private val ThumbnailCorner = 8.dp

/** The callbacks of [RecipeListScreen]. */
data class RecipeListActions(
    val onSearch: (String) -> Unit = {},
    val onCategory: (String?) -> Unit = {},
    val onToggleKeyword: (String) -> Unit = {},
    val onAttentionOnly: (Boolean) -> Unit = {},
    val onClearSearchAndFilters: () -> Unit = {},
    val onSort: (SortOrder) -> Unit = {},
    val onShowAttention: () -> Unit = {},
    val onOpenRecipe: (Long) -> Unit = {},
    val onCreate: () -> Unit = {},
    val onOpenAccount: () -> Unit = {},
    val onOpenSyncStatus: () -> Unit = {},
)

/** The recipe list with its [viewModel]; the callbacks lead to other screens. */
@Composable
fun RecipeListRoute(
    viewModel: RecipeListViewModel,
    onOpenRecipe: (Long) -> Unit = {},
    onCreate: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    onOpenSyncStatus: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RecipeListScreen(
        state = state,
        actions = RecipeListActions(
            onSearch = viewModel::setSearch,
            onCategory = viewModel::setCategory,
            onToggleKeyword = viewModel::toggleKeyword,
            onAttentionOnly = viewModel::setAttentionOnly,
            onClearSearchAndFilters = viewModel::clearSearchAndFilters,
            onSort = viewModel::setSort,
            onShowAttention = viewModel::showAttention,
            onOpenRecipe = onOpenRecipe,
            onCreate = onCreate,
            onOpenAccount = onOpenAccount,
            onOpenSyncStatus = onOpenSyncStatus,
        ),
    )
}

/**
 * The start screen: every local recipe with search, filters, sort order and the sync status bar (spec
 * recipe-browsing, design D8 of recipe-browse-edit).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    state: RecipeListUiState,
    modifier: Modifier = Modifier,
    actions: RecipeListActions = RecipeListActions(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
                actions = {
                    SortMenu(state.listState.sort, actions.onSort)
                    IconButton(onClick = actions.onOpenAccount) {
                        Icon(
                            painter = painterResource(R.drawable.ic_account_circle),
                            contentDescription = stringResource(R.string.start_account),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onCreate,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.recipes_new)) },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            SyncStatusBar(state.bar, onOpen = actions.onOpenSyncStatus, onShowAttention = actions.onShowAttention)
            SearchField(state.listState.search, actions.onSearch)
            FilterRow(state, actions)
            when {
                state.loading -> Centered { CircularProgressIndicator() }
                state.empty != null -> EmptyState(state.empty, actions)
                else -> RecipeItems(state.items, actions.onOpenRecipe)
            }
        }
    }
}

@Composable
private fun SortMenu(current: SortOrder, onSort: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.recipes_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                SortOrder.NAME to R.string.recipes_sort_name,
                SortOrder.LAST_CHANGE to R.string.recipes_sort_last_change,
            )
                .forEach { (order, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        leadingIcon = { RadioButton(selected = order == current, onClick = null) },
                        onClick = {
                            open = false
                            onSort(order)
                        },
                    )
                }
        }
    }
}

@Composable
private fun SearchField(text: String, onSearch: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onSearch,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.recipes_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onSearch("") }) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.recipes_search_clear),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = ItemSpacing),
    )
}

@Composable
private fun RecipeItems(items: List<RecipeListItem>, onOpen: (Long) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = RecipeListItem::localId) { item ->
            ListItem(
                headlineContent = { Text(item.name) },
                supportingContent = item.category?.let { category -> { Text(category) } },
                leadingContent = {
                    RecipeImage(
                        file = item.thumbnail,
                        version = item.thumbnailVersion,
                        modifier = Modifier.size(ThumbnailSize).clip(RoundedCornerShape(ThumbnailCorner)),
                    )
                },
                trailingContent = item.marker?.let { marker -> { Marker(marker) } },
                modifier = Modifier.clickable { onOpen(item.localId) },
            )
        }
    }
}

@Composable
private fun Marker(marker: RecipeMarker) {
    val (icon, label, tint) = when (marker) {
        RecipeMarker.PENDING -> Triple(
            R.drawable.ic_cloud_upload,
            R.string.recipes_marker_pending,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        RecipeMarker.CONFLICT -> Triple(
            R.drawable.ic_call_split,
            R.string.recipes_marker_conflict,
            MaterialTheme.colorScheme.error,
        )

        RecipeMarker.ATTENTION -> Triple(
            R.drawable.ic_warning,
            R.string.recipes_marker_attention,
            MaterialTheme.colorScheme.error,
        )
    }
    Icon(painterResource(icon), contentDescription = stringResource(label), tint = tint)
}

@Composable
private fun EmptyState(empty: EmptyList, actions: RecipeListActions) {
    Centered {
        when (empty) {
            EmptyList.Downloading -> {
                CircularProgressIndicator()
                CenteredText(stringResource(R.string.recipes_empty_downloading))
            }

            is EmptyList.FirstSyncFailed -> {
                CenteredText(stringResource(R.string.recipes_empty_failed))
                CenteredText(empty.problem.message(), MaterialTheme.colorScheme.error)
            }

            EmptyList.NoRecipes -> {
                CenteredText(stringResource(R.string.recipes_empty_none))
                OutlinedButton(onClick = actions.onCreate) { Text(stringResource(R.string.recipes_empty_create)) }
            }

            EmptyList.NothingFound -> {
                CenteredText(stringResource(R.string.recipes_empty_nothing_found))
                OutlinedButton(onClick = actions.onClearSearchAndFilters) {
                    Text(stringResource(R.string.recipes_clear_filters))
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(ItemSpacing, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun CenteredText(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(text = text, color = color, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
}
