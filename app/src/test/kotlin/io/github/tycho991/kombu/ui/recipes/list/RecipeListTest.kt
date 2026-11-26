package io.github.tycho991.kombu.ui.recipes.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.SyncProblem
import io.github.tycho991.kombu.data.recipes.SyncStatus
import io.github.tycho991.kombu.data.recipes.UploadError
import io.github.tycho991.kombu.ui.TestApp
import io.github.tycho991.kombu.ui.TestAppRule
import io.github.tycho991.kombu.ui.theme.KombuTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** Task 4.2: the recipe list with search, filters, markers, sort order and empty states, in both languages. */
// A tall screen, so that the lazy list composes every entry.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2000dp")
class RecipeListTest {
    @get:Rule(order = 0)
    val apps = TestAppRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val status = MutableStateFlow(SyncStatus(lastSuccessAt = 1_000L))
    private val running = MutableStateFlow(false)
    private val app: TestApp by lazy { apps.create(status = status) }
    private val events = mutableListOf<String>()

    private fun json(text: String) = RemoteRecipe(Json.parseToJsonElement(text) as JsonObject)

    private fun recipe(name: String, category: String, keywords: String, extra: String = "") =
        json("""{"name":"$name","recipeCategory":"$category","keywords":"$keywords"$extra}""")

    private fun insert(recipe: LocalRecipe) = runBlocking { app.store.insert(recipe) }

    private fun household() {
        val chana = recipe(
            "Chana Masala",
            "Curry",
            "vegetarisch,indisch",
            ""","recipeIngredient":["400 g Kichererbsen"],"dateModified":"2024-01-01 10:00:00"""",
        )
        insert(LocalRecipe(remoteId = "1", base = chana, state = RecipeState.SYNCED))
        val dal = recipe("Dal", "Curry", "vegetarisch,indisch,vegan", ""","dateModified":"2024-02-01 10:00:00"""")
        insert(LocalRecipe(remoteId = "2", base = dal, state = RecipeState.SYNCED))
        val soup = recipe("Linsensuppe", "Suppe & Eintopf", "vegetarisch", ""","dateModified":"2025-06-01 10:00:00"""")
        insert(LocalRecipe(remoteId = "3", base = soup, state = RecipeState.SYNCED))
    }

    private fun attention() {
        val goulash = recipe("Gulasch", "Eintopf", "")
        insert(
            LocalRecipe(
                remoteId = "4",
                base = goulash,
                local = goulash,
                server = goulash,
                state = RecipeState.CONFLICT,
                conflict = ConflictKind.BOTH_CHANGED,
            ),
        )
        insert(
            LocalRecipe(
                local = recipe("Pizza", "Backen", ""),
                state = RecipeState.CREATED,
                uploadError = UploadError.NAME_TAKEN,
            ),
        )
        val quiche = recipe("Quiche", "Backen", "")
        insert(LocalRecipe(remoteId = "6", base = quiche, local = quiche, state = RecipeState.CHANGED))
    }

    private fun viewModel(locale: Locale = Locale.ENGLISH, saved: SavedStateHandle = SavedStateHandle()) =
        RecipeListViewModel(app.recipes, app.browse, status, running, saved, locale)

    private fun show(locale: Locale = Locale.ENGLISH) {
        val list = viewModel(locale)
        composeRule.setContent {
            KombuTheme {
                RecipeListRoute(
                    viewModel = list,
                    onOpenRecipe = { events += "open $it" },
                    onCreate = { events += "create" },
                    onOpenAccount = { events += "account" },
                    onOpenSyncStatus = { events += "status" },
                )
            }
        }
    }

    private fun waitFor(text: String) = composeRule.waitUntil(TIMEOUT) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitUntilGone(text: String) = composeRule.waitUntil(TIMEOUT) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }

    private fun shows(vararg texts: String) = texts.forEach {
        waitFor(it)
        composeRule.onNodeWithText(it).assertIsDisplayed()
    }

    private fun top(text: String) = composeRule.onNodeWithText(text).getBoundsInRoot().top

    @Test
    @Config(qualifiers = "+en")
    fun showsEveryRecipeWithItsCategory() {
        household()
        show()

        shows("Chana Masala", "Dal", "Linsensuppe", "Suppe & Eintopf")
        assertTrue(top("Chana Masala") < top("Dal"))
        composeRule.onNodeWithText("Dal").performClick()
        composeRule.onNodeWithText("New recipe", useUnmergedTree = true).performClick()
        composeRule.onNodeWithContentDescription("Account").performClick()
        assertEquals("create", events[1])
        assertEquals("account", events[2])
        assertTrue(events[0].startsWith("open "))
    }

    @Test
    @Config(qualifiers = "+en")
    fun marksPendingConflictAndRefusedRecipes() {
        household()
        attention()
        show()

        shows("Quiche")
        composeRule.onNodeWithContentDescription("Not uploaded yet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Changed in two places").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Needs attention").assertIsDisplayed()
        assertEquals(1, composeRule.onAllNodesWithContentDescription("Not uploaded yet").fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "+de")
    fun marksRecipesInGerman() {
        attention()
        show(Locale.GERMAN)

        shows("Quiche")
        composeRule.onNodeWithContentDescription("Noch nicht hochgeladen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("An zwei Stellen geändert").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Zu klären").assertIsDisplayed()
        shows("Zu klären", "Rezepte")
        composeRule.onNodeWithText("Neues Rezept", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "+en")
    fun searchesAndClearsTheSearch() {
        household()
        show()
        shows("Dal")

        composeRule.onNodeWithText("Search recipes").performTextInput("kichererbsen")
        waitUntilGone("Dal")
        shows("Chana Masala")

        composeRule.onNodeWithContentDescription("Clear search").performClick()
        shows("Dal")
        composeRule.onNodeWithText("Search recipes").performTextInput("pancakes")
        shows("Nothing found.")
        composeRule.onNodeWithText("Clear search and filters").performClick()
        shows("Chana Masala", "Dal", "Linsensuppe")
    }

    @Test
    @Config(qualifiers = "+de")
    fun reportsNothingFoundInGerman() {
        household()
        show(Locale.GERMAN)
        shows("Dal")

        composeRule.onNodeWithText("Rezepte durchsuchen").performTextInput("creme brulee")

        shows("Nichts gefunden.", "Suche und Filter zurücksetzen")
    }

    @Test
    @Config(qualifiers = "+en")
    fun filtersByCategory() {
        household()
        show()
        shows("Linsensuppe")

        composeRule.onNodeWithText("Category").performClick()
        composeRule.onNode(hasText("Curry") and hasAnyAncestor(isPopup())).performClick()

        waitUntilGone("Linsensuppe")
        shows("Chana Masala", "Dal")
    }

    @Test
    @Config(qualifiers = "+en")
    fun filtersByTwoKeywords() {
        household()
        show()
        shows("Linsensuppe")

        composeRule.onNodeWithText("Keywords").performClick()
        composeRule.onNodeWithText("vegetarisch").performClick()
        composeRule.onNodeWithText("indisch").performClick()

        waitUntilGone("Linsensuppe")
        shows("Chana Masala", "Dal", "2 keywords")
    }

    @Test
    @Config(qualifiers = "+en")
    fun theAttentionFilterShowsConflictsAndRefusedUploads() {
        household()
        attention()
        show()
        shows("Quiche")

        composeRule.onNodeWithText("Needs attention").performClick()

        waitUntilGone("Quiche")
        shows("Gulasch", "Pizza")
        composeRule.onNodeWithText("Dal").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+en")
    fun sortsByNameOrByLastChange() {
        household()
        show()
        shows("Linsensuppe")
        assertTrue(top("Chana Masala") < top("Linsensuppe"))

        composeRule.onNodeWithContentDescription("Sort").performClick()
        composeRule.onNodeWithText("By last change").performClick()

        composeRule.waitUntil(TIMEOUT) { top("Linsensuppe") < top("Dal") }
        assertTrue(top("Dal") < top("Chana Masala"))
        composeRule.onNodeWithContentDescription("Sort").performClick()
        composeRule.onNodeWithText("By name").performClick()
        composeRule.waitUntil(TIMEOUT) { top("Chana Masala") < top("Dal") }
    }

    @Test
    @Config(qualifiers = "+en")
    fun explainsTheDownloadBeforeTheFirstSync() {
        status.value = SyncStatus()
        running.value = true
        show()

        shows("Your recipes are being downloaded…")
    }

    @Test
    @Config(qualifiers = "+en")
    fun explainsAFailedFirstSync() {
        status.value = SyncStatus(problem = SyncProblem.Offline)
        show()

        shows("No recipes are on this device yet.")
        composeRule.onNodeWithText("the server cannot be reached", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "+en")
    fun offersToCreateTheFirstRecipe() {
        show()

        shows("There are no recipes yet.")
        composeRule.onNodeWithText("Create a recipe").performClick()
        assertEquals(listOf("create"), events)
    }

    @Test
    @Config(qualifiers = "+de")
    fun explainsEmptyListsInGerman() {
        status.value = SyncStatus()
        running.value = true
        show(Locale.GERMAN)
        shows("Deine Rezepte werden heruntergeladen …")

        composeRule.runOnIdle {
            running.value = false
            status.value = SyncStatus(lastSuccessAt = 1_000L)
        }
        shows("Es gibt noch keine Rezepte.", "Rezept anlegen")

        composeRule.runOnIdle { status.value = SyncStatus(problem = SyncProblem.Offline) }
        shows("Auf diesem Gerät sind noch keine Rezepte.")
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
