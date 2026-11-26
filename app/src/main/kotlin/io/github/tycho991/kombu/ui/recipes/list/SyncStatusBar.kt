package io.github.tycho991.kombu.ui.recipes.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.tycho991.kombu.R
import io.github.tycho991.kombu.data.recipes.SyncProblem
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val BarPadding = 16.dp
private val ProgressSize = 16.dp
private val Spacing = 8.dp

/**
 * The compact sync status above the recipe list (spec "Sync status bar"): the last sync or the current problem,
 * whether a sync runs, and the pending changes. Tapping it calls [onOpen]; the count of recipes that need
 * attention calls [onShowAttention].
 */
@Composable
fun SyncStatusBar(state: SyncBarState, onOpen: () -> Unit, onShowAttention: () -> Unit, modifier: Modifier = Modifier) {
    val problem = state.status.problem
    val alarming = problem == SyncProblem.CredentialsRejected
    Surface(
        onClick = onOpen,
        color = if (alarming) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = BarPadding, vertical = Spacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing),
        ) {
            if (state.running) CircularProgressIndicator(modifier = Modifier.size(ProgressSize), strokeWidth = 2.dp)
            Text(
                text = listOfNotNull(headline(state), pending(state.status.pendingCount)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (state.attentionCount > 0) {
                TextButton(onClick = onShowAttention) {
                    Text(pluralStringResource(R.plurals.bar_attention, state.attentionCount, state.attentionCount))
                }
            }
        }
    }
}

@Composable
private fun headline(state: SyncBarState): String {
    val lastSuccess = state.status.lastSuccessAt
    return when {
        state.running -> stringResource(R.string.sync_running)

        else -> when (state.status.problem) {
            SyncProblem.CredentialsRejected -> stringResource(R.string.bar_sign_in)

            SyncProblem.Offline -> stringResource(R.string.bar_offline)

            is SyncProblem.ServerError -> stringResource(R.string.bar_failed)

            SyncProblem.CookbookMissing, is SyncProblem.CookbookTooOld -> stringResource(R.string.bar_not_possible)

            null -> if (lastSuccess == null) {
                stringResource(R.string.bar_never)
            } else {
                stringResource(R.string.bar_synced_at, shortTime(lastSuccess, LocalConfiguration.current.locales[0]))
            }
        }
    }
}

@Composable
private fun pending(count: Int): String? =
    if (count > 0) pluralStringResource(R.plurals.bar_pending, count, count) else null

/** [epochMillis] as the time of day when it is today, otherwise as the date, in the format of [locale]. */
fun shortTime(epochMillis: Long, locale: Locale, now: Long = System.currentTimeMillis()): String {
    val day = { millis: Long ->
        Calendar.getInstance().apply { timeInMillis = millis }.let { it[Calendar.YEAR] to it[Calendar.DAY_OF_YEAR] }
    }
    val format = if (day(epochMillis) == day(now)) {
        DateFormat.getTimeInstance(DateFormat.SHORT, locale)
    } else {
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
    }
    return format.format(Date(epochMillis))
}
