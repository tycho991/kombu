package io.github.tycho991.kombu.ui.recipes.editor

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import io.github.tycho991.kombu.cookbookclient.RecipeField
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.ui.theme.KombuTheme
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tasks 6.1, 6.3 and 6.4: the editor's fields, the unsaved-changes question and the collision question. */
// A tall screen, so that every field of the form is composed.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h3000dp")
class RecipeEditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(EditorUiState(loading = false))
    private val events = mutableListOf<String>()
    private var back: OnBackPressedDispatcher? = null

    private val filled = EditorUiState(
        loading = false,
        form = EditorForm(
            name = "Soup",
            description = "Warm",
            category = "Soups",
            keywords = listOf("quick"),
            servings = "4",
            prepTime = TimeInput(minutes = "10"),
            cookTime = TimeInput("1", "5"),
            totalTime = TimeInput(raw = "about an hour"),
            ingredients = "200 g lentils",
            steps = listOf("Chop.", "Cook."),
        ),
        categories = listOf("Soups", "Stews"),
        keywords = listOf("quick", "vegetarisch"),
    )

    private fun show() {
        val actions = EditorActions(
            onChange = { change -> state = state.copy(form = change(state.form), hasChanges = true) },
            onSave = { events += "save" },
            onDiscard = { events += "discard" },
            onKeepMine = { events += "mine" },
            onTakeTheirs = { events += "theirs" },
            onSaveAsNew = { events += "new" },
        )
        composeRule.setContent {
            back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            KombuTheme { RecipeEditorScreen(state, actions = actions) }
        }
    }

    private fun field(label: String) = composeRule.onNode(hasSetTextAction() and hasText(label))

    private fun pressBack() = composeRule.runOnUiThread { checkNotNull(back).onBackPressed() }

    @Test
    @Config(qualifiers = "+en")
    fun everyFieldIsShownAndEditable() {
        state = filled
        show()

        listOf(
            "Name",
            "Description",
            "Category",
            "Keywords",
            "Servings",
            "Ingredients, one per line",
            "Step 1",
            "Step 2",
        ).forEach { field(it).assertIsDisplayed() }
        listOf("Preparation time", "Cooking time", "Total time", "Stored as: about an hour", "Steps").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        assertEquals(3, composeRule.onAllNodesWithText("Hours").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("image", substring = true, ignoreCase = true).assertDoesNotExist()

        field("Name").performTextReplacement("Lentil soup")
        field("Servings").performTextReplacement("6")
        field("Keywords").performTextInput("spicy, ")
        composeRule.onNodeWithContentDescription("Remove quick").performClick()
        composeRule.onNodeWithText("Remove").performClick()
        composeRule.onNodeWithText("Add step").performClick()

        val form = state.form
        assertEquals("Lentil soup", form.name)
        assertEquals("6", form.servings)
        assertEquals(listOf("spicy"), form.keywords)
        assertEquals(null, form.totalTime.raw)
        assertEquals(listOf("Chop.", "Cook.", ""), form.steps)
    }

    @Test
    @Config(qualifiers = "+en")
    fun movesAndRemovesSteps() {
        state = filled.copy(form = filled.form.copy(steps = listOf("One", "Two", "Three")))
        show()

        // The n-th button belongs to the n-th step.
        composeRule.onAllNodesWithContentDescription("Move step up")[2].performClick()
        assertEquals(listOf("One", "Three", "Two"), state.form.steps)

        composeRule.onAllNodesWithContentDescription("Move step down")[0].performClick()
        assertEquals(listOf("Three", "One", "Two"), state.form.steps)

        composeRule.onAllNodesWithContentDescription("Remove step")[1].performClick()
        assertEquals(listOf("Three", "Two"), state.form.steps)
    }

    @Test
    @Config(qualifiers = "+en")
    fun showsValidationErrors() {
        state = filled.copy(
            errors = setOf(FormError.NAME_TAKEN, FormError.SERVINGS_INVALID, FormError.COOK_TIME_INVALID),
            saveFailed = true,
        )
        show()

        composeRule.onNodeWithText("Another recipe already has this name.").assertIsDisplayed()
        composeRule.onNodeWithText("Servings must be a whole number of at least 1.").assertIsDisplayed()
        composeRule.onNodeWithText("Enter whole hours and minutes below 60.").assertIsDisplayed()
        composeRule.onNodeWithText("The recipe could not be saved. Your entries are kept.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "+de")
    fun showsTheFieldsAndErrorsInGerman() {
        state = filled.copy(errors = setOf(FormError.NAME_REQUIRED))
        show()

        listOf("Name", "Beschreibung", "Kategorie", "Schlagwörter", "Portionen", "Zutaten, eine pro Zeile", "Schritt 1")
            .forEach { field(it).assertIsDisplayed() }
        composeRule.onNodeWithText("Gib einen Namen ein.").assertIsDisplayed()
        composeRule.onNodeWithText("Gespeichert als: about an hour").assertIsDisplayed()
        composeRule.onNodeWithText("Rezept bearbeiten").assertIsDisplayed()
        composeRule.onNodeWithText("Speichern").performClick()
        assertEquals(listOf("save"), events)
    }

    @Test
    @Config(qualifiers = "+en")
    fun backWithChangesAsksAndKeepsEditingWhenDeclined() {
        state = filled.copy(hasChanges = true)
        show()

        pressBack()
        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep editing").performClick()
        composeRule.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertEquals(emptyList<String>(), events)

        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.onNodeWithText("Discard").performClick()
        assertEquals(listOf("discard"), events)
    }

    @Test
    @Config(qualifiers = "+en")
    fun backWithoutChangesLeaves() {
        state = filled
        show()

        pressBack()

        composeRule.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertEquals(listOf("discard"), events)
    }

    @Test
    @Config(qualifiers = "+en")
    fun aCollisionNamesTheFieldAndOffersBothValues() {
        state = filled.copy(dialog = EditorDialog.Collision(setOf(RecipeField.NAME), EMPTY))
        show()

        composeRule.onNodeWithText("Changed in Nextcloud meanwhile").assertIsDisplayed()
        composeRule.onNodeWithText("this was changed in Nextcloud: name.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Keep mine").performClick()
        composeRule.onNodeWithText("Use Nextcloud's").performClick()
        assertEquals(listOf("mine", "theirs"), events)
    }

    @Test
    @Config(qualifiers = "+de")
    fun asksAboutCollisionsAndDeletionsInGerman() {
        state = filled.copy(dialog = EditorDialog.Collision(setOf(RecipeField.STEPS, RecipeField.NAME), EMPTY))
        show()
        composeRule.onNodeWithText("in Nextcloud geändert: Name, Zubereitung.", substring = true).assertIsDisplayed()

        state = filled.copy(dialog = EditorDialog.Gone)
        composeRule.onNodeWithText("Rezept in Nextcloud gelöscht").assertIsDisplayed()
        composeRule.onNodeWithText("Als neues Rezept speichern").performClick()
        assertEquals(listOf("new"), events)
    }

    @Test
    @Config(qualifiers = "+en")
    fun aNewRecipeHasItsOwnTitle() {
        state = EditorUiState(loading = false, isNew = true)
        show()

        composeRule.onNodeWithText("New recipe").assertIsDisplayed()
    }

    private companion object {
        val EMPTY = RemoteRecipe(JsonObject(emptyMap()))
    }
}
