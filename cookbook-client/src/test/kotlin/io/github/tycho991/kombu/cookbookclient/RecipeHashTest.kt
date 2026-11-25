package io.github.tycho991.kombu.cookbookclient

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class RecipeHashTest {
    private fun hash(text: String): String = RecipeHash.of(checkNotNull(RemoteRecipe.parse(text)))

    @Test
    fun `matches SHA-256 values computed independently for the fixtures`() {
        // Computed with Python: json.dumps(sort_keys=True, separators=(",", ":"), ensure_ascii=False) and hashlib.
        assertEquals(
            "fa9ec20aaef40b2fba18e125b1d3a3fad413a9fea83703733c6f18203342752c",
            RecipeHash.of(Fixtures.numberId),
        )
        assertEquals(
            "b2cc9f162eb896e49fbd55589634dfe72ec2f2e2d68cc16b852294e320e02794",
            RecipeHash.of(Fixtures.stringId),
        )
    }

    @Test
    fun `does not depend on key order, also in nested objects`() {
        assertEquals(
            hash("""{"name":"A","nutrition":{"calories":"100","fat":"2 g"},"recipeYield":2}"""),
            hash("""{"recipeYield":2,"nutrition":{"fat":"2 g","calories":"100"},"name":"A"}"""),
        )
    }

    @Test
    fun `does not depend on whitespace`() {
        assertEquals(
            hash("""{"name":"A","tool":["pot","spoon"]}"""),
            hash("{\n  \"name\" : \"A\",\n  \"tool\" : [ \"pot\" , \"spoon\" ]\n}"),
        )
    }

    @Test
    fun `ignores the fields the server sets by itself`() {
        val base = Fixtures.numberId
        val changedByServer =
            RecipeHash.SERVER_FIELDS.fold(base) { recipe, field -> recipe.with(field, JsonPrimitive("server $field")) }

        assertEquals(RecipeHash.of(base), RecipeHash.of(changedByServer))
        assertEquals(RecipeHash.of(base), RecipeHash.of(base.without("imageUrl").without("printImage")))
    }

    @Test
    fun `changes when any other field changes`() {
        val base = Fixtures.stringId
        val original = RecipeHash.of(base)

        base.json.keys.filter { it !in RecipeHash.SERVER_FIELDS }.forEach { field ->
            assertNotEquals(original, RecipeHash.of(base.with(field, JsonPrimitive("changed"))), field)
            assertNotEquals(original, RecipeHash.of(base.without(field)), field)
        }
        assertNotEquals(original, RecipeHash.of(base.with("newField", JsonPrimitive(1))))
    }

    @Test
    fun `changes when an array item changes or moves`() {
        assertNotEquals(hash("""{"tool":["pot","spoon"]}"""), hash("""{"tool":["pot","ladle"]}"""))
        assertNotEquals(hash("""{"tool":["pot","spoon"]}"""), hash("""{"tool":["spoon","pot"]}"""))
        assertNotEquals(hash("""{"steps":[{"text":"a"}]}"""), hash("""{"steps":[{"text":"b"}]}"""))
    }

    @Test
    fun `changes when only the JSON type changes`() {
        assertNotEquals(hash("""{"recipeYield":1}"""), hash("""{"recipeYield":"1"}"""))
        assertNotEquals(hash("""{"nutrition":{}}"""), hash("""{"nutrition":[]}"""))
        assertNotEquals(hash("""{"printed":true}"""), hash("""{"printed":"true"}"""))
        assertNotEquals(hash("""{"totalTime":null}"""), hash("""{"totalTime":"null"}"""))
    }

    @Test
    fun `writes a canonical form with sorted keys, escaped strings and number literals`() {
        val replacement = "\uFFFD"
        val pot = "\uD83C\uDF72"
        val json =
            buildJsonObject {
                put("b", JsonArray(listOf(JsonPrimitive(1.5), JsonPrimitive(true))))
                put("a", "say \"hi\"\n")
                put(pot, JsonObject(emptyMap()))
                put(replacement, 2)
            }

        // U+FFFD sorts before U+1F372 by code point, although its UTF-16 unit is larger than the surrogate's.
        assertEquals(
            """{"a":"say \"hi\"\n","b":[1.5,true],"$replacement":2,"$pot":{}}""",
            RecipeHash.canonical(json),
        )
    }
}
