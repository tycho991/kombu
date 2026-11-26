package io.github.tycho991.kombu.ui.recipes.conflict

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.tycho991.kombu.cookbookclient.RecipeField
import io.github.tycho991.kombu.cookbookclient.RecipeTime
import io.github.tycho991.kombu.cookbookclient.RecipeValues
import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.Resolution
import io.github.tycho991.kombu.ui.theme.KombuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/** Task 7.1: the conflict view per kind and language (spec "Conflict view"). */
// A tall screen, so that every compared field and both choices are composed.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h3000dp")
class ConflictScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val row = LocalRecipe(localId = 1, state = RecipeState.CONFLICT)
    private val mine = RecipeValues(name = "Soup", description = "Mine", cookTime = RecipeTime.Known(30.minutes))
    private val theirs = RecipeValues(name = "Web soup", description = "Theirs", ingredients = listOf("a", "b"))
    private var state: ConflictUiState by mutableStateOf(ConflictUiState.Loading)
    private val events = mutableListOf<String>()

    private fun show() {
        composeRule.setContent {
            KombuTheme {
                ConflictScreen(
                    state = state,
                    onBack = { events += "back" },
                    onChoose = { events += it.name },
                    onConfirm = { events += "confirm" },
                    onCancel = { events += "cancel" },
                )
            }
        }
    }

    private fun shows(vararg texts: String) = texts.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }

    private fun bothChanged(pending: Resolution? = null) = ConflictUiState.Shown(
        recipe = row,
        kind = ConflictKind.BOTH_CHANGED,
        mine = mine,
        theirs = theirs,
        fields = listOf(RecipeField.NAME, RecipeField.DESCRIPTION, RecipeField.COOK_TIME, RecipeField.INGREDIENTS),
        otherFields = 2,
        pending = pending?.let { PendingChoice(row, it) },
    )

    @Test
    @Config(qualifiers = "+en")
    fun listsBothValuesOfEachDifferingField() {
        state = bothChanged()
        show()

        shows(
            "This recipe was changed in Kombu and in Nextcloud.",
            "name",
            "description",
            "cooking time",
            "ingredients",
        )
        shows("Soup", "Web soup", "Mine", "Theirs", "30 min", "a\nb")
        assertEquals(4, composeRule.onAllNodesWithText("In Kombu").fetchSemanticsNodes().size)
        assertEquals(2, composeRule.onAllNodesWithText("(empty)").fetchSemanticsNodes().size)
        shows("2 other fields differ; Kombu does not show them.")

        composeRule.onNodeWithText("Keep my version").performClick()
        composeRule.onNodeWithText("Use the Nextcloud version").performClick()
        assertEquals(listOf("KEEP_MINE", "TAKE_SERVER"), events)
    }

    @Test
    @Config(qualifiers = "+en")
    fun confirmsAChoiceAndSaysWhatItDiscards() {
        state = bothChanged(pending = Resolution.TAKE_SERVER)
        show()

        shows("Apply your choice?", "Kombu takes the version from Nextcloud. Your changes are discarded.")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onAllNodesWithText("Use the Nextcloud version")[1].performClick()
        assertEquals(listOf("cancel", "confirm"), events)
    }

    @Test
    @Config(qualifiers = "+en")
    fun showsTheKombuVersionOfARecipeDeletedInNextcloud() {
        state = ConflictUiState.Shown(row, ConflictKind.DELETED_ON_SERVER, mine = mine, theirs = null)
        show()

        shows("This recipe was changed in Kombu and deleted in Nextcloud.", "The version in Kombu", "Soup", "Mine")
        shows("Keep the recipe", "Delete it here too")
        composeRule.onNodeWithText("In Nextcloud").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+en")
    fun showsTheNextcloudVersionOfARecipeDeletedHere() {
        state = ConflictUiState.Shown(
            row,
            ConflictKind.CHANGED_ON_SERVER_DELETED_HERE,
            mine = null,
            theirs = theirs,
            pending = PendingChoice(row, Resolution.KEEP_MINE),
        )
        show()

        shows("This recipe was deleted in Kombu and changed in Nextcloud.", "The version in Nextcloud", "Web soup")
        shows("Kombu deletes the recipe in Nextcloud at the next sync. The changes made in Nextcloud are discarded.")
        assertEquals(2, composeRule.onAllNodesWithText("Delete it anyway").fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "+en")
    fun saysWhenTheConflictChangedMeanwhile() {
        state = bothChanged().copy(changedMeanwhile = true)
        show()

        shows("This recipe changed again in the meantime. Nothing was applied; check the conflict again.")
    }

    @Test
    @Config(qualifiers = "+de")
    fun showsEveryKindInGerman() {
        state = bothChanged(pending = Resolution.KEEP_MINE)
        show()
        shows("Konflikt klären", "Kochzeit", "30 Min.", "Deine Wahl anwenden?")
        shows("Kombu lädt deine Version beim nächsten Abgleich hoch. Die Änderungen in Nextcloud werden verworfen.")
        shows("2 weitere Felder unterscheiden sich; Kombu zeigt sie nicht an.")

        state = ConflictUiState.Shown(row, ConflictKind.DELETED_ON_SERVER, mine = mine, theirs = null)
        shows("Die Version in Kombu", "Rezept behalten", "Auch hier löschen")

        state = ConflictUiState.Shown(row, ConflictKind.CHANGED_ON_SERVER_DELETED_HERE, mine = null, theirs = theirs)
        shows("Die Version in Nextcloud", "Trotzdem löschen", "Version aus Nextcloud behalten")
    }
}
