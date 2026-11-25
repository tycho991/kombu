package io.github.tycho991.kombu.cookbookclient

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

/**
 * Content hash of a recipe, used to tell whether two server versions of a recipe differ (design D3, O11).
 *
 * The hash is SHA-256, in lower-case hex, over a canonical form: object keys sorted by code point at every level,
 * arrays in their order, no whitespace, strings escaped as JSON, numbers as their JSON literal. The top-level
 * fields in [SERVER_FIELDS] are left out, because the server sets them by itself.
 */
public object RecipeHash {
    /**
     * Top-level fields the server writes on its own and that are not recipe content:
     * - `id`: the folder id, sent back as a number or a string;
     * - `dateModified`: set to the current time on every save;
     * - `imageUrl`, `imagePlaceholderUrl`: links the server adds when reading, with a cache-busting parameter;
     * - `printImage`: a per-user app setting the server adds to every recipe it returns.
     */
    public val SERVER_FIELDS: Set<String> = setOf("id", "dateModified", "imageUrl", "imagePlaceholderUrl", "printImage")

    private const val HEX_DIGITS = "0123456789abcdef"
    private const val NIBBLE_BITS = 4
    private const val NIBBLE_MASK = 0x0f

    /** The hash of [recipe]'s content. */
    public fun of(recipe: RemoteRecipe): String = of(recipe.json)

    /** The hash of the recipe object [json]'s content. */
    public fun of(json: JsonObject): String {
        val content = JsonObject(json.filterKeys { it !in SERVER_FIELDS })
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical(content).toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt()
                append(HEX_DIGITS[value shr NIBBLE_BITS and NIBBLE_MASK])
                append(HEX_DIGITS[value and NIBBLE_MASK])
            }
        }
    }

    /** The canonical JSON text of [element]: sorted keys, no whitespace. */
    public fun canonical(element: JsonElement): String = buildString { appendCanonical(element) }

    private fun StringBuilder.appendCanonical(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                append('{')
                element.keys.sortedWith(CodePointOrder).forEachIndexed { index, key ->
                    if (index > 0) append(',')
                    append(JsonPrimitive(key).toString())
                    append(':')
                    appendCanonical(element.getValue(key))
                }
                append('}')
            }

            is JsonArray -> {
                append('[')
                element.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    appendCanonical(item)
                }
                append(']')
            }

            // A string primitive prints quoted and escaped; numbers, booleans and null print their literal.
            is JsonPrimitive -> append(element.toString())
        }
    }

    /** Orders strings by Unicode code point; [String.compareTo] compares UTF-16 units instead. */
    private object CodePointOrder : Comparator<String> {
        override fun compare(a: String, b: String): Int {
            val left = a.codePoints().iterator()
            val right = b.codePoints().iterator()
            while (left.hasNext() && right.hasNext()) {
                val difference = left.nextInt().compareTo(right.nextInt())
                if (difference != 0) return difference
            }
            return left.hasNext().compareTo(right.hasNext())
        }
    }
}
