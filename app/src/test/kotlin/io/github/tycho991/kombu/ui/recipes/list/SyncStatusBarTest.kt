package io.github.tycho991.kombu.ui.recipes.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.tycho991.kombu.data.recipes.SyncProblem
import io.github.tycho991.kombu.data.recipes.SyncStatus
import io.github.tycho991.kombu.ui.theme.KombuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** Task 4.3: the compact sync status above the list, per language (spec "Sync status bar"). */
@RunWith(RobolectricTestRunner::class)
class SyncStatusBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(SyncBarState())
    private val events = mutableListOf<String>()
    private val synced = 1_700_000_000_000L

    private fun show() {
        composeRule.setContent {
            KombuTheme {
                SyncStatusBar(state, onOpen = { events += "open" }, onShowAttention = { events += "attention" })
            }
        }
    }

    private fun shows(text: String) = composeRule.onNodeWithText(text).assertIsDisplayed()

    @Test
    @Config(qualifiers = "en")
    fun showsEachStateInEnglish() {
        state = SyncBarState(SyncStatus(recipeCount = 3, lastSuccessAt = synced))
        show()
        shows("Synced ${shortTime(synced, Locale.ENGLISH)}")
        composeRule.onNodeWithText("waiting", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("need", substring = true).assertDoesNotExist()

        state = SyncBarState(SyncStatus(lastSuccessAt = synced), running = true)
        shows("Syncing…")

        state = SyncBarState(SyncStatus(pendingCount = 2, lastSuccessAt = synced, problem = SyncProblem.Offline))
        shows("Offline · 2 changes waiting")

        state = SyncBarState(SyncStatus(problem = SyncProblem.ServerError(503)))
        shows("Sync failed")

        state = SyncBarState(SyncStatus(problem = SyncProblem.CookbookMissing))
        shows("Sync not possible")

        state = SyncBarState(SyncStatus(conflictCount = 2, lastSuccessAt = synced), attentionCount = 2)
        composeRule.onNodeWithText("2 need attention").performClick()

        state = SyncBarState(SyncStatus(problem = SyncProblem.CredentialsRejected))
        composeRule.onNodeWithText("Sign in again to keep syncing").performClick()

        assertEquals(listOf("attention", "open"), events)
    }

    @Test
    @Config(qualifiers = "en")
    fun showsANeverSyncedCopy() {
        show()
        shows("Not synced yet")
    }

    @Test
    @Config(qualifiers = "de")
    fun showsEachStateInGerman() {
        state = SyncBarState(SyncStatus(lastSuccessAt = synced))
        show()
        shows("Abgeglichen: ${shortTime(synced, Locale.GERMAN)}")

        state = SyncBarState(running = true)
        shows("Wird abgeglichen …")

        state = SyncBarState(SyncStatus(pendingCount = 2, problem = SyncProblem.Offline))
        shows("Offline · 2 Änderungen warten")

        state = SyncBarState(SyncStatus(pendingCount = 1, lastSuccessAt = synced), attentionCount = 1)
        shows("1 zu klären")

        state = SyncBarState(SyncStatus(problem = SyncProblem.CredentialsRejected))
        shows("Erneut anmelden, um weiter abzugleichen")
    }

    @Test
    fun showsTheTimeForTodayAndTheDateBefore() {
        val now = synced + 60_000
        assertEquals(
            java.text.DateFormat.getTimeInstance(
                java.text.DateFormat.SHORT,
                Locale.ENGLISH,
            ).format(java.util.Date(synced)),
            shortTime(synced, Locale.ENGLISH, now),
        )
        assertEquals(
            java.text.DateFormat.getDateInstance(
                java.text.DateFormat.MEDIUM,
                Locale.ENGLISH,
            ).format(java.util.Date(synced)),
            shortTime(synced, Locale.ENGLISH, now + 3 * 86_400_000L),
        )
    }
}
