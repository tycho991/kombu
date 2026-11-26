package io.github.tycho991.kombu.cookbookclient

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecipeDiffTest {
    private fun recipe(json: String) = RemoteRecipe(Json.parseToJsonElement(json) as JsonObject)

    private fun diff(a: String, b: String) = RecipeDiff.between(recipe(a), recipe(b))

    private val soup = """
        {"name":"Soup","description":"Warm","recipeYield":4,"cookTime":"PT1H","keywords":"a,b",
         "recipeIngredient":["1 onion"],"recipeInstructions":["Cook."],"nutrition":{"calories":300}}
    """

    @Test
    fun `reports nothing for equal recipes`() {
        val result = diff(soup, soup)

        assertEquals(RecipeDiff(emptyList(), 0), result)
        assertTrue(result.isEmpty)
    }

    @Test
    fun `reports each shown field that differs, in field order`() {
        val other = """
            {"name":"Stew","description":"Hearty","recipeYield":4,"cookTime":"PT2H","keywords":"a,b",
             "recipeIngredient":["1 onion","1 carrot"],"recipeInstructions":["Cook."],"nutrition":{"calories":300}}
        """

        val result = diff(soup, other)

        assertEquals(
            listOf(RecipeField.NAME, RecipeField.DESCRIPTION, RecipeField.COOK_TIME, RecipeField.INGREDIENTS),
            result.fields,
        )
        assertEquals(0, result.otherKeys)
        assertFalse(result.isEmpty)
    }

    @Test
    fun `counts other differing keys, including added and removed ones`() {
        val result = diff(
            """{"name":"Soup","nutrition":{"calories":300},"tool":["pot"]}""",
            """{"name":"Soup","nutrition":{"calories":350},"author":"Sam"}""",
        )

        assertEquals(emptyList<RecipeField>(), result.fields)
        assertEquals(3, result.otherKeys)
    }

    @Test
    fun `reports a field that one version lacks`() {
        assertEquals(listOf(RecipeField.SERVINGS), diff("""{"name":"A"}""", """{"name":"A","recipeYield":2}""").fields)
    }

    @Test
    fun `ignores the server-managed keys`() {
        val result = diff(
            """{"id":1,"name":"Soup","dateModified":"2024-01-01","imageUrl":"/a?t=1","printImage":true}""",
            """{"id":"1","name":"Soup","dateModified":"2025-02-02","imageUrl":"/a?t=2","printImage":false,
                "imagePlaceholderUrl":"/p"}""",
        )

        assertTrue(result.isEmpty)
    }

    @Test
    fun `ignores key order and number formatting`() {
        val result = diff(
            """{"name":"Soup","recipeYield":1,"nutrition":{"fat":2,"calories":300.0}}""",
            """{"nutrition":{"calories":3e2,"fat":2.0},"recipeYield":1.0,"name":"Soup"}""",
        )

        assertTrue(result.isEmpty)
    }

    @Test
    fun `keeps a number and the same text apart`() {
        assertEquals(1, diff("""{"name":"A","x":1}""", """{"name":"A","x":"1"}""").otherKeys)
    }

    @Test
    fun `counts a shown field that differs only in its stored form among the others`() {
        val result = diff(
            """{"name":"Soup","keywords":"a, b","recipeInstructions":[{"@type":"HowToStep","text":"Cook."}]}""",
            """{"name":"Soup","keywords":["a","b"],"recipeInstructions":["Cook."]}""",
        )

        assertEquals(emptyList<RecipeField>(), result.fields)
        assertEquals(2, result.otherKeys)
    }
}
