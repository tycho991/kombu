package io.github.tycho991.kombu.ui.recipes.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.UploadError
import io.github.tycho991.kombu.ui.TestApp
import io.github.tycho991.kombu.ui.TestAppRule
import io.github.tycho991.kombu.ui.theme.KombuTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** Tasks 5.1-5.3: the recipe view, scaled servings and deletion, in both languages. */
// A tall screen, so that every section of a complete recipe is composed and visible.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h3000dp")
class RecipeDetailTest {
    @get:Rule(order = 0)
    val apps = TestAppRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val app: TestApp by lazy { apps.create() }
    private val events = mutableListOf<String>()

    private fun json(text: String) = RemoteRecipe(Json.parseToJsonElement(text) as JsonObject)

    private val complete = json(
        """
        {"name":"Pfannkuchen","recipeCategory":"Frühstück","keywords":"süß,schnell","description":"Fluffig.",
         "recipeYield":4,"prepTime":"PT10M","cookTime":"PT1H5M","totalTime":"PT1H15M",
         "recipeIngredient":["Für den Teig:","200 g Mehl","Saft von 3 Orangen","Salz und Pfeffer"],
         "recipeInstructions":[{"@type":"HowToStep","text":"Verrühren."},{"@type":"HowToStep","text":"Backen."}],
         "url":"https://recipes.example.org/pancakes"}
        """,
    )

    private fun insert(recipe: LocalRecipe): Long = runBlocking { app.store.insert(recipe).localId }

    private fun synced(recipe: RemoteRecipe) = insert(
        LocalRecipe(remoteId = "1", base = recipe, state = RecipeState.SYNCED),
    )

    private fun show(localId: Long, locale: Locale = Locale.ENGLISH) {
        val detail = RecipeDetailViewModel(localId, app.recipes, androidx.lifecycle.SavedStateHandle(), locale)
        composeRule.setContent {
            KombuTheme {
                RecipeDetailRoute(
                    viewModel = detail,
                    onBack = { events += "back" },
                    onClose = { events += "close" },
                    onEdit = { events += "edit" },
                    onResolve = { events += "resolve" },
                )
            }
        }
    }

    private fun waitFor(text: String) = composeRule.waitUntil(TIMEOUT) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    /** Waits for [event], letting the main looper run the view model's work in between. */
    private fun waitForEvent(event: String) {
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (event !in events) {
            check(System.currentTimeMillis() < deadline) { "No \"$event\" within $TIMEOUT ms; events: $events" }
            Thread.sleep(POLL_MILLIS)
            composeRule.waitForIdle()
        }
    }

    private fun shows(vararg texts: String) = texts.forEach {
        waitFor(it)
        composeRule.onNodeWithText(it).assertIsDisplayed()
    }

    private fun absent(vararg texts: String) = texts.forEach {
        composeRule.onNodeWithText(it, substring = true).assertDoesNotExist()
    }

    // 5.1 Recipe view

    @Test
    @Config(qualifiers = "+en")
    fun showsACompleteRecipe() {
        show(synced(complete))

        shows("Pfannkuchen", "Frühstück", "süß · schnell", "Fluffig.")
        shows("Preparation: 10 min", "Cooking: 1 hr 5 min", "Total: 1 hr 15 min")
        shows("4 servings", "Ingredients", "Für den Teig", "200 g Mehl", "Steps", "1.", "Verrühren.", "2.", "Backen.")
        shows("Open original recipe")
        absent("not scaled", "not uploaded")
    }

    @Test
    @Config(qualifiers = "+de")
    fun showsACompleteRecipeInGerman() {
        show(synced(complete), Locale.GERMAN)

        shows("Vorbereitung: 10 Min.", "Kochen: 1 Std. 5 Min.", "Gesamt: 1 Std. 15 Min.")
        shows("4 Portionen", "Zutaten", "Zubereitung", "Originalrezept öffnen")
        composeRule.onNodeWithContentDescription("Bearbeiten").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Löschen").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "+en")
    fun leavesOutWhatASparseRecipeDoesNotHave() {
        show(synced(json("""{"name":"Gartensalat","recipeIngredient":["Salat","Dressing"]}""")))

        shows("Gartensalat", "Ingredients", "Salat", "Dressing")
        absent("Steps", "Preparation", "Cooking", "Total", "servings", "Open original recipe")
        composeRule.onNodeWithContentDescription("More servings").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+en")
    fun showsAnUnreadableTimeAsStored() {
        show(synced(json("""{"name":"Brot","cookTime":"about an hour","prepTime":"PT20S"}""")))

        shows("Cooking: about an hour", "Preparation: < 1 min")
    }

    @Test
    @Config(qualifiers = "+en")
    fun explainsAPendingChange() {
        val soup = json("""{"name":"Soup"}""")
        show(
            insert(
                LocalRecipe(
                    remoteId = "1",
                    base = soup,
                    local = soup.with("description", Json.parseToJsonElement("\"x\"")),
                    state = RecipeState.CHANGED,
                ),
            ),
        )

        shows("This change is not uploaded yet. Kombu uploads it at the next sync.")
    }

    @Test
    @Config(qualifiers = "+en")
    fun aConflictRoutesEditAndDeleteToTheConflictView() {
        val soup = json("""{"name":"Soup"}""")
        show(
            insert(
                LocalRecipe(
                    remoteId = "1",
                    base = soup,
                    local = soup,
                    server = soup,
                    state = RecipeState.CONFLICT,
                    conflict = ConflictKind.BOTH_CHANGED,
                ),
            ),
        )

        shows("This recipe was changed in Kombu and in Nextcloud.")
        composeRule.onNodeWithText("Resolve").performClick()
        composeRule.onNodeWithContentDescription("Edit").performClick()
        composeRule.onNodeWithContentDescription("Delete").performClick()

        assertEquals(listOf("resolve", "resolve", "resolve"), events)
        absent("Delete recipe?")
    }

    @Test
    @Config(qualifiers = "+de")
    fun explainsConflictKindsInGerman() {
        val soup = json("""{"name":"Suppe"}""")
        show(
            insert(
                LocalRecipe(
                    remoteId = "1",
                    base = soup,
                    local = soup,
                    state = RecipeState.CONFLICT,
                    conflict = ConflictKind.DELETED_ON_SERVER,
                ),
            ),
        )

        shows("Dieses Rezept wurde in Kombu geändert und in Nextcloud gelöscht.", "Klären")
    }

    @Test
    @Config(qualifiers = "+en")
    fun aConflictDeletedHereShowsTheNextcloudVersion() {
        val soup = json("""{"name":"Soup from the web"}""")
        show(
            insert(
                LocalRecipe(
                    remoteId = "1",
                    base = soup,
                    server = soup,
                    state = RecipeState.CONFLICT,
                    conflict = ConflictKind.CHANGED_ON_SERVER_DELETED_HERE,
                ),
            ),
        )

        shows("Soup from the web", "This recipe was deleted in Kombu and changed in Nextcloud.")
    }

    @Test
    @Config(qualifiers = "+en")
    fun aRefusedNameOffersToEditIt() {
        show(
            insert(
                LocalRecipe(
                    local = json("""{"name":"Soup"}"""),
                    state = RecipeState.CREATED,
                    uploadError = UploadError.NAME_TAKEN,
                ),
            ),
        )

        shows("Nextcloud refused this recipe: another recipe there already has this name.")
        composeRule.onNodeWithText("Edit name").performClick()
        assertEquals(listOf("edit"), events)
    }

    @Test
    @Config(qualifiers = "+de")
    fun explainsRefusedUploadsInGerman() {
        show(
            insert(
                LocalRecipe(
                    local = json("""{"name":"Suppe"}"""),
                    state = RecipeState.CREATED,
                    uploadError = UploadError.REJECTED,
                ),
            ),
        )

        shows("Nextcloud hat dieses Rezept abgelehnt.", "Bearbeiten")
    }

    @Test
    @Config(qualifiers = "+en")
    fun closesWhenTheRecipeIsRemovedWhileOpen() {
        val localId = synced(complete)
        show(localId)
        shows("Pfannkuchen")

        runBlocking { app.store.remove(checkNotNull(app.recipes.recipe(localId).first())) }

        waitForEvent("close")
    }

    // 5.2 Scaled servings

    @Test
    @Config(qualifiers = "+en")
    fun scalesFromFourToSixServingsAndMarksUnscaledLines() {
        val localId = synced(complete)
        show(localId)
        shows("200 g Mehl")

        composeRule.onNodeWithContentDescription("More servings").performClick()
        composeRule.onNodeWithContentDescription("More servings").performClick()

        shows("6 servings", "300 g Mehl", "Saft von 3 Orangen", "Salz und Pfeffer", "Für den Teig")
        assertEquals(2, composeRule.onAllNodesWithText("not scaled").fetchSemanticsNodes().size)

        composeRule.onNodeWithContentDescription("Fewer servings").performClick()
        composeRule.onNodeWithContentDescription("Fewer servings").performClick()
        shows("4 servings", "200 g Mehl")
        absent("not scaled")
    }

    @Test
    @Config(qualifiers = "+de")
    fun marksUnscaledLinesInGerman() {
        show(synced(complete), Locale.GERMAN)
        shows("200 g Mehl")

        composeRule.onNodeWithContentDescription("Weniger Portionen").performClick()
        composeRule.onNodeWithContentDescription("Weniger Portionen").performClick()

        shows("2 Portionen", "100 g Mehl")
        assertEquals(2, composeRule.onAllNodesWithText("nicht umgerechnet").fetchSemanticsNodes().size)
    }

    // 5.3 Delete

    @Test
    @Config(qualifiers = "+en")
    fun deletingAfterConfirmationRemovesTheRecipeAndSchedulesASync() {
        val localId = synced(complete)
        show(localId)
        shows("Pfannkuchen")

        composeRule.onNodeWithContentDescription("Delete").performClick()
        shows("Delete recipe?")
        composeRule.onNodeWithText("Kombu deletes \"Pfannkuchen\"", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("Delete")[0].performClick()

        waitForEvent("close")
        runBlocking {
            assertEquals(emptyList<LocalRecipe>(), app.recipes.recipes().first())
            assertEquals(RecipeState.DELETED, app.recipes.recipe(localId).first()?.state)
            // The changed counter is what makes the app schedule a sync (design D10).
            assertEquals(1L, app.store.syncState().localChangeSeq)
        }
    }

    @Test
    @Config(qualifiers = "+de")
    fun cancellingTheDeletionChangesNothing() {
        val localId = synced(complete)
        show(localId, Locale.GERMAN)
        shows("Pfannkuchen")

        composeRule.onNodeWithContentDescription("Löschen").performClick()
        shows("Rezept löschen?")
        composeRule.onNodeWithText("Kombu löscht „Pfannkuchen“", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Abbrechen").performClick()

        absent("Rezept löschen?")
        runBlocking {
            assertEquals(RecipeState.SYNCED, app.recipes.recipe(localId).first()?.state)
            assertEquals(0L, app.store.syncState().localChangeSeq)
        }
    }

    private companion object {
        const val TIMEOUT = 5_000L
        const val POLL_MILLIS = 50L
    }
}
