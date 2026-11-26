package io.github.tycho991.kombu.cookbookclient

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/**
 * The differences between two versions of a recipe, as the conflict view shows them (design D3 of
 * recipe-browse-edit).
 */
public data class RecipeDiff(
    /** The shown fields whose values differ, in the order of [RecipeField]. */
    val fields: List<RecipeField>,
    /**
     * The number of other top-level keys that differ: keys Kombu does not show, such as `nutrition`, and shown
     * fields that differ only in their stored form, such as keywords stored as a list and as a string.
     */
    val otherKeys: Int,
) {
    /** `true` when the two versions have the same content. */
    val isEmpty: Boolean get() = fields.isEmpty() && otherKeys == 0

    /** Compares recipe versions. */
    public companion object {
        /**
         * Compares [a] and [b] key by key.
         *
         * The keys in [RecipeHash.SERVER_FIELDS] are ignored, because the server sets them by itself. Values are
         * compared in canonical form, so key order and number formatting (`1` and `1.0`) do not count. A shown
         * field is reported in [fields] when its [RecipeFields.read] values differ; every other differing key
         * counts towards [otherKeys].
         */
        public fun between(a: RemoteRecipe, b: RemoteRecipe): RecipeDiff {
            val keys = (a.json.keys + b.json.keys) - RecipeHash.SERVER_FIELDS
            val differingKeys = keys.filter { comparable(a.json[it]) != comparable(b.json[it]) }
            val shown = RecipeFields.read(a).differingFields(RecipeFields.read(b))
            val fields = RecipeField.entries.filter { it in shown && it.key in differingKeys }
            return RecipeDiff(fields = fields, otherKeys = differingKeys.size - fields.size)
        }

        private fun comparable(element: JsonElement?): String? = element?.let { RecipeHash.canonical(normalised(it)) }

        /** The element with every JSON number written in one form, so that `1`, `1.0` and `1e0` compare equal. */
        private fun normalised(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.mapValues { normalised(it.value) })
            is JsonArray -> JsonArray(element.map(::normalised))
            is JsonPrimitive -> number(element)?.let(::JsonPrimitive) ?: element
        }

        private fun number(primitive: JsonPrimitive): BigDecimal? =
            if (primitive.isString) null else primitive.content.toBigDecimalOrNull()?.stripTrailingZeros()
    }
}
