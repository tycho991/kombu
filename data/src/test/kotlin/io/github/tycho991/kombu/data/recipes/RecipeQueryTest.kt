package io.github.tycho991.kombu.data.recipes

import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Task 3.1: search, filters and sorting of the recipe list (design D7). */
class RecipeQueryTest {
    private val german = RecipeQuery(Locale.GERMAN)

    private fun row(
        name: String,
        category: String? = null,
        keywords: List<String> = emptyList(),
        ingredients: List<String> = emptyList(),
        modified: String? = null,
        localChangeAt: Long? = null,
        state: RecipeState = RecipeState.SYNCED,
        uploadError: UploadError? = null,
    ): LocalRecipe {
        val json = RemoteRecipe(
            buildJsonObject {
                put("name", name)
                category?.let { put("recipeCategory", it) }
                put("keywords", keywords.joinToString(","))
                put("recipeIngredient", JsonArray(ingredients.map(::JsonPrimitive)))
                modified?.let { put("dateModified", it) }
            },
        )
        return LocalRecipe(
            localId = name.hashCode().toLong(),
            base = json,
            state = state,
            uploadError = uploadError,
            localChangeAt = localChangeAt,
        )
    }

    private val chana = row(
        "Chana Masala",
        category = "Curry",
        keywords = listOf("vegetarisch", "indisch"),
        ingredients = listOf("400 g Kichererbsen", "1 Zwiebel"),
    )
    private val brulee = row("Crème brûlée", category = "Dessert", keywords = listOf("süß"))
    private val dal = row("Dal", category = "Curry", keywords = listOf("vegetarisch", "Indisch", "vegan"))
    private val soup = row("Linsensuppe", category = "Suppe & Eintopf", keywords = listOf("vegetarisch"))
    private val strudel = row("Apfelstrudel", category = "Dessert", ingredients = listOf("Grieß", "Äpfel"))
    private val all = listOf(chana, brulee, dal, soup, strudel)

    private fun names(state: ListState, recipes: List<LocalRecipe> = all, query: RecipeQuery = german) =
        query.query(query.index(recipes), state).map { it.shown?.name }

    @Test
    fun `an empty search shows every recipe by name`() {
        assertEquals(
            listOf("Apfelstrudel", "Chana Masala", "Crème brûlée", "Dal", "Linsensuppe"),
            names(ListState()),
        )
    }

    @Test
    fun `finds the search text in name, category, keywords and ingredients`() {
        assertEquals(listOf("Chana Masala"), names(ListState(search = "kichererbsen")))
        assertEquals(listOf("Linsensuppe"), names(ListState(search = "eintopf")))
        assertEquals(listOf("Dal"), names(ListState(search = "VEGAN")))
        assertEquals(listOf("Dal"), names(ListState(search = "dal")))
    }

    @Test
    fun `every term must match`() {
        assertEquals(listOf("Chana Masala", "Dal"), names(ListState(search = "indisch  vegetarisch")))
        assertEquals(listOf("Dal"), names(ListState(search = " indisch vegan ")))
        assertEquals(emptyList<String>(), names(ListState(search = "indisch dessert")))
    }

    @Test
    fun `ignores case, accents and sharp s`() {
        assertEquals(listOf("Crème brûlée"), names(ListState(search = "creme brulee")))
        assertEquals(listOf("Crème brûlée"), names(ListState(search = "SUSS")))
        assertEquals(listOf("Apfelstrudel"), names(ListState(search = "griess")))
        assertEquals(listOf("Apfelstrudel"), names(ListState(search = "Grieß")))
        assertEquals(listOf("Apfelstrudel"), names(ListState(search = "apfel")))
    }

    @Test
    fun `filters by category, keywords and attention, combined with the search`() {
        assertEquals(listOf("Linsensuppe"), names(ListState(category = "Suppe & Eintopf")))
        assertEquals(listOf("Chana Masala", "Dal"), names(ListState(category = "curry")))
        assertEquals(listOf("Chana Masala", "Dal"), names(ListState(keywords = setOf("vegetarisch", "indisch"))))
        assertEquals(listOf("Dal"), names(ListState(category = "Curry", search = "vegan")))
        assertEquals(emptyList<String>(), names(ListState(category = "Dessert", keywords = setOf("indisch"))))
    }

    @Test
    fun `the attention filter shows conflicts and refused uploads`() {
        val conflict = row("Gulasch", state = RecipeState.CONFLICT)
        val refused = row("Pizza", state = RecipeState.CREATED, uploadError = UploadError.NAME_TAKEN)
        val pending = row("Quiche", state = RecipeState.CHANGED)

        assertEquals(
            listOf("Gulasch", "Pizza"),
            names(ListState(attentionOnly = true), all + listOf(conflict, refused, pending)),
        )
        assertTrue(conflict.needsAttention)
        assertFalse(pending.needsAttention)
    }

    @Test
    fun `sorts umlauts with their base letter in German and English`() {
        val recipes = listOf(row("Birnen"), row("Äpfel im Schlafrock"), row("Auflauf"), row("Zucchini"), row("ananas"))
        val expected = listOf("ananas", "Äpfel im Schlafrock", "Auflauf", "Birnen", "Zucchini")

        assertEquals(expected, names(ListState(), recipes))
        assertEquals(expected, names(ListState(), recipes, RecipeQuery(Locale.ENGLISH)))
    }

    @Test
    fun `sorts by last change, newest first, unreadable dates last`() {
        val recipes = listOf(
            row("Old", modified = "2024-01-01 10:00:00"),
            row("Server", modified = "2025-03-01T08:00:00+0000"),
            row("Offset", modified = "2025-02-01T08:00:00+01:00"),
            row("Mine", modified = "2023-01-01 10:00:00", localChangeAt = 1_800_000_000_000),
            row("Broken", modified = "yesterday"),
            row("Undated"),
        )

        assertEquals(
            listOf("Mine", "Server", "Offset", "Old", "Broken", "Undated"),
            names(ListState(sort = SortOrder.LAST_CHANGE), recipes),
        )
    }

    @Test
    fun `reads the date forms of Cookbook`() {
        assertEquals(1_704_103_200_000, RecipeQuery.parseDate("2024-01-01 10:00:00"))
        assertEquals(1_704_103_200_000, RecipeQuery.parseDate("2024-01-01T10:00:00"))
        assertEquals(1_704_103_200_000, RecipeQuery.parseDate("2024-01-01T10:00:00+0000"))
        assertEquals(1_704_099_600_000, RecipeQuery.parseDate("2024-01-01T10:00:00+01:00"))
        assertNull(RecipeQuery.parseDate("01.01.2024"))
    }

    @Test
    fun `lists distinct categories and keywords alphabetically`() {
        assertEquals(listOf("Curry", "Dessert", "Suppe & Eintopf"), german.categories(all))
        assertEquals(listOf("indisch", "süß", "vegan", "vegetarisch"), german.keywords(all))
        assertEquals(emptyList<String>(), german.categories(listOf(row("Plain"))))
    }

    @Test
    fun `tells whether the list is narrowed`() {
        assertFalse(ListState(sort = SortOrder.LAST_CHANGE).isNarrowed)
        assertTrue(ListState(search = "x").isNarrowed)
        assertTrue(ListState(category = "x").isNarrowed)
        assertTrue(ListState(keywords = setOf("x")).isNarrowed)
        assertTrue(ListState(attentionOnly = true).isNarrowed)
        assertFalse(ListState(search = "  ").isNarrowed)
    }

    @Test
    fun `a recipe deleted here and changed on the server is searchable by its server version`() {
        val conflict = LocalRecipe(
            server = RemoteRecipe(buildJsonObject { put("name", "Ghost") }),
            state = RecipeState.CONFLICT,
            conflict = ConflictKind.CHANGED_ON_SERVER_DELETED_HERE,
        )

        assertEquals(listOf("Ghost"), names(ListState(search = "ghost"), listOf(conflict)))
    }
}
