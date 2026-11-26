package io.github.tycho991.kombu.ui.start

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

/** The sync status on the start screen, per language (spec recipe-sync, "Sync status"). */
@RunWith(RobolectricTestRunner::class)
class StartScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(StartUiState())
    private val events = mutableListOf<String>()
    private val synced = 1_700_000_000_000L

    private fun show(darkTheme: Boolean = false) {
        val actions =
            StartActions(
                onBack = { events += "back" },
                onSyncNow = { events += "syncNow" },
                onSignInAgain = { events += "signInAgain" },
            )
        composeRule.setContent { KombuTheme(darkTheme = darkTheme) { StartScreen(state, actions = actions) } }
    }

    private fun text(value: String) = composeRule.onNodeWithText(value, substring = true).assertIsDisplayed()

    private fun assertProblems(messages: Map<SyncProblem, String>) {
        messages.forEach { (problem, message) ->
            state = StartUiState(SyncStatus(recipeCount = 2, lastSuccessAt = synced, problem = problem))
            text(message)
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun showsTheEmptyStateInEnglish() {
        show()

        composeRule.onNodeWithText("Sync").assertIsDisplayed()
        text("0 recipes on this device")
        text("Not synced yet.")
        composeRule.onNodeWithText("Sync now").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("syncNow", "back"), events)
    }

    @Test
    @Config(qualifiers = "en")
    fun showsTheStateAfterASyncInEnglish() {
        state = StartUiState(SyncStatus(recipeCount = 1, pendingCount = 3, conflictCount = 1, lastSuccessAt = synced))
        show()

        text("1 recipe on this device")
        text("Last synced: ${formatTime(synced, Locale.ENGLISH)}")
        text("3 changes are not uploaded yet.")
        text("1 recipe was changed in two places and needs your decision.")
    }

    @Test
    @Config(qualifiers = "en")
    fun showsARunningSyncInEnglish() {
        state = StartUiState(SyncStatus(recipeCount = 64), running = true)
        show()

        text("Syncing…")
        composeRule.onNodeWithText("Sync now").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "en")
    fun showsEveryProblemInEnglish() {
        show()
        assertProblems(
            mapOf(
                SyncProblem.Offline to "server cannot be reached",
                SyncProblem.ServerError(503) to "(HTTP 503)",
                SyncProblem.CookbookMissing to "Cookbook app is not available",
                SyncProblem.CookbookTooOld(
                    "0.10.2",
                ) to "needs Cookbook 0.10.3 or newer to sync, but your Nextcloud has Cookbook 0.10.2",
                SyncProblem.CredentialsRejected to "no longer accepts Kombu's app password",
            ),
        )
        text("Last synced: ${formatTime(synced, Locale.ENGLISH)}")
        composeRule.onNodeWithText("Sign in again").performClick()
        assertEquals(listOf("signInAgain"), events)
    }

    @Test
    @Config(qualifiers = "de")
    fun showsTheStatusInGerman() {
        state = StartUiState(SyncStatus(recipeCount = 2, pendingCount = 1, conflictCount = 2, lastSuccessAt = synced))
        show()

        composeRule.onNodeWithText("Abgleich").assertIsDisplayed()
        text("2 Rezepte auf diesem Gerät")
        text("Zuletzt abgeglichen: ${formatTime(synced, Locale.GERMAN)}")
        text("1 Änderung ist noch nicht hochgeladen.")
        text("2 Rezepte wurden an zwei Stellen geändert")
        text("Jetzt abgleichen")

        state = StartUiState(running = true)
        text("Noch nicht abgeglichen.")
        text("Wird abgeglichen …")
    }

    @Test
    @Config(qualifiers = "de")
    fun showsEveryProblemInGerman() {
        show()
        assertProblems(
            mapOf(
                SyncProblem.Offline to "Server ist nicht erreichbar",
                SyncProblem.ServerError(503) to "(HTTP 503)",
                SyncProblem.CookbookMissing to "App Cookbook ist für dein Konto nicht verfügbar",
                SyncProblem.CookbookTooOld("0.10.2") to "braucht zum Abgleichen Cookbook 0.10.3 oder neuer",
                SyncProblem.CredentialsRejected to "akzeptiert das App-Passwort von Kombu nicht mehr",
            ),
        )
        text("Erneut anmelden")
    }

    @Test
    @Config(qualifiers = "fr")
    fun fallsBackToEnglishForUnsupportedLanguage() {
        show()
        text("Not synced yet.")
    }

    @Test
    @Config(qualifiers = "en-night")
    fun rendersInDarkTheme() {
        show(darkTheme = true)
        text("0 recipes on this device")
    }
}
