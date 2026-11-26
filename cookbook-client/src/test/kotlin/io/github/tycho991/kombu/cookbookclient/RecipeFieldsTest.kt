package io.github.tycho991.kombu.cookbookclient

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class RecipeFieldsTest {
    private fun recipe(json: String) = RemoteRecipe(Json.parseToJsonElement(json) as JsonObject)

    private fun read(json: String) = RecipeFields.read(recipe(json))

    /** A recipe with every field in a form Kombu does not write itself, plus fields Kombu does not show. */
    private val rich = recipe(
        """
        {"id":12,"name":"Soup","description":"Warm","recipeCategory":"Soups","keywords":"warm, quick",
         "recipeYield":"4 Portionen","prepTime":"PT10M","cookTime":"PT1H5M","totalTime":"about an hour",
         "recipeIngredient":["200 g lentils","1 onion"],
         "recipeInstructions":[{"@type":"HowToStep","text":"Chop."},{"@type":"HowToStep","text":"Cook."}],
         "url":"https://recipes.example.org/soup","nutrition":{"calories":"300 kcal"},"image":"x.jpg",
         "tool":["pot"],"dateModified":"2024-01-01 10:00:00"}
        """,
    )

    @Nested
    inner class Reading {
        @Test
        fun `reads every field of a complete recipe`() {
            assertEquals(
                RecipeValues(
                    name = "Soup",
                    description = "Warm",
                    category = "Soups",
                    keywords = listOf("warm", "quick"),
                    servings = 4,
                    prepTime = RecipeTime.Known(10.minutes),
                    cookTime = RecipeTime.Known(1.hours + 5.minutes),
                    totalTime = RecipeTime.Raw("about an hour"),
                    ingredients = listOf("200 g lentils", "1 onion"),
                    steps = listOf("Chop.", "Cook."),
                    sourceUrl = "https://recipes.example.org/soup",
                ),
                RecipeFields.read(rich),
            )
        }

        @Test
        fun `reads an empty recipe as absent everywhere`() {
            assertEquals(RecipeValues(), read("{}"))
        }

        @Test
        fun `reads keywords as a string and as a list`() {
            assertEquals(listOf("a", "b"), read("""{"keywords":"a,b"}""").keywords)
            assertEquals(listOf("a", "b"), read("""{"keywords":["a"," b ",""]}""").keywords)
        }

        @Test
        fun `reads servings as a number or a string starting with one`() {
            assertEquals(4, read("""{"recipeYield":4}""").servings)
            assertEquals(4, read("""{"recipeYield":4.0}""").servings)
            assertEquals(4, read("""{"recipeYield":"4"}""").servings)
            assertEquals(4, read("""{"recipeYield":"4 Portionen"}""").servings)
            assertEquals(6, read("""{"recipeYield":" 6 servings"}""").servings)
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "0", "\"0\"", "-2", "2.5", "\"\"", "\"some\"", "true", "null", "[4]", "{}", "\"99999999999\"",
            ],
        )
        fun `reads other servings as absent`(value: String) {
            assertNull(read("""{"recipeYield":$value}""").servings)
        }

        @Test
        fun `reads times as durations or raw text`() {
            assertEquals(RecipeTime.Known(45.minutes), read("""{"prepTime":"PT45M"}""").prepTime)
            assertEquals(RecipeTime.Known(2.hours), read("""{"cookTime":" PT2H "}""").cookTime)
            assertEquals(RecipeTime.Raw("10 min"), read("""{"totalTime":"10 min"}""").totalTime)
        }

        @ParameterizedTest
        @ValueSource(strings = ["\"\"", "\"PT0S\"", "\"PT0H0M\"", "\"P0D\"", "30", "null", "[]", "{}"])
        fun `reads empty, zero and mistyped times as absent`(value: String) {
            assertNull(read("""{"cookTime":$value}""").cookTime)
        }

        @Test
        fun `reads ingredients as a list or as lines of one string`() {
            assertEquals(listOf("a", "b"), read("""{"recipeIngredient":["a","","  ","b",3,null]}""").ingredients)
            assertEquals(listOf("a", "b"), read("""{"recipeIngredient":"a\n\nb\n"}""").ingredients)
            assertEquals(emptyList<String>(), read("""{"recipeIngredient":{"a":"b"}}""").ingredients)
        }

        @Test
        fun `reads steps in every shape`() {
            assertEquals(listOf("One", "Two"), read("""{"recipeInstructions":"One\n\nTwo"}""").steps)
            assertEquals(listOf("One", "Two"), read("""{"recipeInstructions":["One","Two",""]}""").steps)
            assertEquals(
                listOf("One", "Two", "Three"),
                read(
                    """
                    {"recipeInstructions":[
                      {"@type":"HowToSection","name":"Dough","itemListElement":[
                        {"@type":"HowToStep","text":"One"},{"@type":"HowToStep","text":"Two"}]},
                      "Three",{"@type":"HowToStep"},{"@type":"HowToStep","text":5},7]}
                    """,
                ).steps,
            )
            assertEquals(emptyList<String>(), read("""{"recipeInstructions":42}""").steps)
        }

        @Test
        fun `reads only web links as the source`() {
            assertEquals("http://example.org/a", read("""{"url":"http://example.org/a"}""").sourceUrl)
            assertNull(read("""{"url":"javascript:alert(1)"}""").sourceUrl)
            assertNull(read("""{"url":"file:///etc/passwd"}""").sourceUrl)
            assertNull(read("""{"url":"https://exa mple.org"}""").sourceUrl)
            assertNull(read("""{"url":["https://example.org"]}""").sourceUrl)
        }

        @Test
        fun `reads mistyped text fields as absent`() {
            val values = read("""{"description":12,"recipeCategory":["Soups"],"name":"  "}""")

            assertNull(values.description)
            assertNull(values.category)
            assertNull(values.name)
        }

        @Test
        fun `reads blank text fields as absent`() {
            assertNull(read("""{"description":"  ","recipeCategory":""}""").description)
            assertNull(read("""{"description":"  ","recipeCategory":""}""").category)
        }
    }

    @Nested
    inner class Writing {
        private val changed = RecipeValues(
            name = "Stew",
            description = "Hearty",
            category = "Mains",
            keywords = listOf("hearty", "slow"),
            servings = 6,
            prepTime = RecipeTime.Known(20.minutes),
            cookTime = RecipeTime.Known(2.hours + 30.minutes),
            totalTime = RecipeTime.Raw("all afternoon"),
            ingredients = listOf("500 g beef", "2 carrots"),
            steps = listOf("Brown.", "Simmer."),
        )

        private val expectedJson = mapOf(
            RecipeField.NAME to "\"Stew\"",
            RecipeField.DESCRIPTION to "\"Hearty\"",
            RecipeField.CATEGORY to "\"Mains\"",
            RecipeField.KEYWORDS to "\"hearty,slow\"",
            RecipeField.SERVINGS to "6",
            RecipeField.PREP_TIME to "\"PT20M\"",
            RecipeField.COOK_TIME to "\"PT2H30M\"",
            RecipeField.TOTAL_TIME to "\"all afternoon\"",
            RecipeField.INGREDIENTS to "[\"500 g beef\",\"2 carrots\"]",
            RecipeField.STEPS to "[\"Brown.\",\"Simmer.\"]",
        )

        @ParameterizedTest
        @EnumSource(RecipeField::class)
        fun `writing one field leaves every other key identical`(field: RecipeField) {
            val written = RecipeFields.write(rich, field, changed)

            assertEquals(expectedJson.getValue(field), written.json.getValue(field.key).toString())
            assertEquals(
                RecipeHash.canonical(JsonObject(rich.json - field.key)),
                RecipeHash.canonical(JsonObject(written.json - field.key)),
            )
            assertEquals(changed[field], RecipeFields.read(written)[field])
        }

        @Test
        fun `keeps the key order of the recipe`() {
            val written = RecipeFields.write(rich, RecipeField.NAME, changed)

            assertEquals(rich.json.keys.toList(), written.json.keys.toList())
        }

        @Test
        fun `writes several fields at once`() {
            val written = RecipeFields.write(rich, setOf(RecipeField.NAME, RecipeField.SERVINGS), changed)

            assertEquals(
                setOf(RecipeField.NAME, RecipeField.SERVINGS),
                RecipeFields.read(written).differingFields(RecipeFields.read(rich)),
            )
        }

        @Test
        fun `writing nothing returns the same recipe`() {
            assertEquals(rich, RecipeFields.write(rich, emptySet(), changed))
        }

        @ParameterizedTest
        @EnumSource(names = ["CATEGORY", "SERVINGS", "PREP_TIME", "COOK_TIME", "TOTAL_TIME"])
        fun `cleared values remove their key`(field: RecipeField) {
            val written = RecipeFields.write(rich, field, RecipeValues())

            assertFalse(field.key in written.json)
            assertNull(RecipeFields.read(written)[field])
        }

        @Test
        fun `cleared text and lists are written empty`() {
            val written = RecipeFields.write(
                rich,
                setOf(RecipeField.DESCRIPTION, RecipeField.KEYWORDS, RecipeField.INGREDIENTS, RecipeField.STEPS),
                RecipeValues(),
            )

            assertEquals("\"\"", written.json.getValue("description").toString())
            assertEquals("\"\"", written.json.getValue("keywords").toString())
            assertEquals("[]", written.json.getValue("recipeIngredient").toString())
            assertEquals("[]", written.json.getValue("recipeInstructions").toString())
        }

        @Test
        fun `adds a field the recipe did not have`() {
            val written = RecipeFields.write(recipe("""{"name":"Bare"}"""), RecipeField.SERVINGS, changed)

            assertEquals(6, RecipeFields.read(written).servings)
        }
    }

    @Nested
    inner class Fields {
        @Test
        fun `finds fields by their key`() {
            RecipeField.entries.forEach { assertEquals(it, RecipeField.ofKey(it.key)) }
            assertNull(RecipeField.ofKey("nutrition"))
        }

        @Test
        fun `lists the fields that differ`() {
            val a = RecipeValues(name = "A", keywords = listOf("x"), servings = 2)
            val b = RecipeValues(name = "A", keywords = listOf("y"), servings = 3)

            assertEquals(setOf(RecipeField.KEYWORDS, RecipeField.SERVINGS), a.differingFields(b))
            assertEquals(emptySet<RecipeField>(), a.differingFields(a.copy()))
        }
    }
}
