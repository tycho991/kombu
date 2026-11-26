package io.github.tycho991.kombu.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.tycho991.kombu.R
import io.github.tycho991.kombu.cookbookclient.CookbookVersion
import io.github.tycho991.kombu.data.recipes.SyncProblem
import io.github.tycho991.kombu.data.recipes.SyncStatus
import io.github.tycho991.kombu.ui.theme.KombuTheme
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val ContentPadding = 24.dp
private val ItemSpacing = 12.dp

/** The callbacks of [StartScreen]. */
data class StartActions(
    val onBack: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onSignInAgain: () -> Unit = {},
)

/**
 * The full sync status, opened from the bar above the recipe list (spec recipe-browsing, "Sync status bar"): the
 * state of the local recipe copy and its sync, with "sync now" and "sign in again".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(state: StartUiState, modifier: Modifier = Modifier, actions: StartActions = StartActions()) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_status_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.account_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(ContentPadding),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            SyncSummary(state.status)
            if (state.running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.sync_running), style = MaterialTheme.typography.bodyMedium)
            }
            state.status.problem?.let { problem ->
                Text(
                    text = problem.message(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (problem == SyncProblem.CredentialsRejected) {
                    Button(onClick = actions.onSignInAgain) { Text(stringResource(R.string.sync_sign_in_again)) }
                }
            }
            if (state.status.problem != SyncProblem.CredentialsRejected) {
                OutlinedButton(onClick = actions.onSyncNow, enabled = !state.running) {
                    Text(stringResource(R.string.sync_now))
                }
            }
        }
    }
}

@Composable
private fun SyncSummary(status: SyncStatus) {
    Text(
        text = pluralStringResource(R.plurals.sync_recipe_count, status.recipeCount, status.recipeCount),
        style = MaterialTheme.typography.bodyLarge,
    )
    val lastSuccess = status.lastSuccessAt
    Text(
        text =
        if (lastSuccess == null) {
            stringResource(R.string.sync_never)
        } else {
            stringResource(R.string.sync_last_success, formatTime(lastSuccess, LocalConfiguration.current.locales[0]))
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (status.pendingCount > 0) {
        Text(pluralStringResource(R.plurals.sync_pending, status.pendingCount, status.pendingCount))
    }
    if (status.conflictCount > 0) {
        Text(pluralStringResource(R.plurals.sync_conflicts, status.conflictCount, status.conflictCount))
    }
}

/** [epochMillis] as date and time in the format of [locale]. */
fun formatTime(epochMillis: Long, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(epochMillis))

/** The user-facing explanation of a sync problem (design D12), from the string resources. */
@Composable
fun SyncProblem.message(): String = when (this) {
    SyncProblem.Offline -> stringResource(R.string.sync_problem_offline)

    is SyncProblem.ServerError -> stringResource(R.string.sync_problem_server, statusCode)

    SyncProblem.CredentialsRejected -> stringResource(R.string.sync_problem_credentials)

    SyncProblem.CookbookMissing -> stringResource(R.string.sync_problem_cookbook_missing)

    is SyncProblem.CookbookTooOld ->
        stringResource(R.string.sync_problem_cookbook_too_old, version, CookbookVersion.MINIMUM.toString())
}

@Preview(showBackground = true)
@Composable
private fun StartScreenPreview() {
    KombuTheme {
        StartScreen(StartUiState(SyncStatus(recipeCount = 64, pendingCount = 2, lastSuccessAt = 0)))
    }
}
