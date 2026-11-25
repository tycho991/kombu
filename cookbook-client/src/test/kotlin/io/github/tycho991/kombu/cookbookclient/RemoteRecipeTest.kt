package io.github.tycho991.kombu.cookbookclient

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteRecipeTest {
    @Test
    fun `reads a numeric id as text`() {
        assertEquals("1234", Fixtures.numberId.id)
    }

    @Test
    fun `reads a string id`() {
        assertEquals("5678", Fixtures.stringId.id)
    }

    @Test
    fun `reads the name, image, category and keywords`() {
        val recipe = Fixtures.numberId

        assertEquals("Lemon Lentil Soup", recipe.name)
        assertEquals("https://recipes.example.org/images/lentil-soup.jpg", recipe.image)
        assertEquals("Soups", recipe.category)
        assertEquals(listOf("vegetarian", "quick", "lentils"), recipe.keywords)
    }

    @Test
    fun `reads keywords from a list and treats empty image and category as missing`() {
        val recipe = Fixtures.stringId

        assertEquals(listOf("Winter", "Kohl"), recipe.keywords)
        assertNull(recipe.image)
        assertNull(recipe.category)
    }

    @Test
    fun `reads a null and a missing dateModified as null`() {
        assertNull(Fixtures.numberId.dateModified)
        assertNull(Fixtures.stringId.dateModified)
        assertEquals(
            "2024-05-01T10:00:00+0200",
            Fixtures.numberId.with("dateModified", JsonPrimitive("2024-05-01T10:00:00+0200")).dateModified,
        )
    }

    @Test
    fun `treats fields of an unexpected type as missing`() {
        val recipe =
            RemoteRecipe(
                buildJsonObject {
                    put("name", JsonObject(emptyMap()))
                    put("keywords", JsonNull)
                    put("image", JsonArray(emptyList()))
                },
            )

        assertNull(recipe.name)
        assertNull(recipe.id)
        assertEquals(emptyList<String>(), recipe.keywords)
        assertNull(recipe.image)
    }

    @Test
    fun `keeps every other field and its type when one field changes`() {
        val original = Fixtures.stringId

        val changed = original.with("name", JsonPrimitive("Grünkohl klassisch"))

        assertEquals("Grünkohl klassisch", changed.name)
        assertEquals(original.json - "name", changed.json - "name")
        assertTrue(changed.json["nutrition"] is JsonArray)
        assertTrue(changed.json["author"] is JsonObject)
        assertEquals(original.json["video"], changed.json["video"])
    }

    @Test
    fun `keeps every field shape of the numeric-id recipe through an edit`() {
        val original = Fixtures.numberId

        val changed = original.with("recipeYield", JsonPrimitive(6))

        assertTrue(changed.json["nutrition"] is JsonObject)
        assertEquals(JsonPrimitive("Sam Example"), changed.json["author"])
        assertEquals(JsonNull, changed.json["dateModified"])
        assertEquals(JsonPrimitive(1234), changed.json["id"])
    }

    @Test
    fun `keeps an author given as a list`() {
        val authors = JsonArray(listOf(JsonPrimitive("Sam"), buildJsonObject { put("name", "Kim") }))
        val recipe = Fixtures.numberId.with("author", authors)

        assertEquals(authors, recipe.with("name", JsonPrimitive("Other")).json["author"])
    }

    @Test
    fun `removes a field without touching the others`() {
        val recipe = Fixtures.numberId.without("id")

        assertNull(recipe.id)
        assertEquals(Fixtures.numberId.json - "id", recipe.json)
    }

    @Test
    fun `round-trips through its JSON text`() {
        val recipe = Fixtures.stringId

        assertEquals(recipe, RemoteRecipe.parse(recipe.encode()))
    }

    @Test
    fun `parses only JSON objects`() {
        assertNull(RemoteRecipe.parse("[]"))
        assertNull(RemoteRecipe.parse("<html>"))
    }

    @Test
    fun `reads stubs with string and numeric ids and skips entries without id`() {
        val stubs =
            parseStubs(
                parseElement(
                    """
                    [
                      {"id":"1","recipe_id":1,"name":"A","dateModified":"2024-01-02T10:00:00+0100"},
                      {"recipe_id":2,"name":"B","dateModified":null},
                      {"name":"No id"},
                      "not an object"
                    ]
                    """.trimIndent(),
                ),
            )

        assertEquals(
            listOf(
                RecipeStub("1", "A", "2024-01-02T10:00:00+0100"),
                RecipeStub("2", "B", null),
            ),
            stubs,
        )
        assertNull(parseStubs(parseElement("{}")))
    }
}
