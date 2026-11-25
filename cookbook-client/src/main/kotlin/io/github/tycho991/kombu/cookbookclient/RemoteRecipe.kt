package io.github.tycho991.kombu.cookbookclient

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A recipe as the Cookbook server stores it: a schema.org `Recipe` JSON object, kept as raw [json].
 *
 * Kombu never maps a recipe to a typed model, so fields it does not know survive a round trip with their value
 * and JSON type. The accessors read the few fields the sync needs and tolerate the varying types real recipes use.
 */
public data class RemoteRecipe(
    /** The complete recipe object, including fields Kombu does not know. */
    val json: JsonObject,
) {
    /** The server's id of the recipe, as text; older recipes store it as a number. `null` when missing. */
    val id: String? get() = json["id"].textOrNull()

    /** The recipe name, or `null` when it is missing or blank. */
    val name: String? get() = json["name"].textOrNull()

    /** The raw `dateModified` value, or `null` when it is missing or JSON `null`. */
    val dateModified: String? get() = json["dateModified"].textOrNull()

    /** The `image` field (a URL or path), or `null` when the recipe has no image. */
    val image: String? get() = json["image"].textOrNull()

    /** The recipe category, or `null` when it has none. */
    val category: String? get() = json["recipeCategory"].textOrNull()

    /** The keywords, from a comma-separated string or a list; blank entries are dropped. */
    val keywords: List<String>
        get() =
            when (val value = json["keywords"]) {
                is JsonArray -> value.mapNotNull { it.textOrNull() }
                else -> value.textOrNull()?.split(',').orEmpty()
            }.map(String::trim).filter(String::isNotEmpty)

    /** A copy with [field] set to [value]; every other field is kept unchanged. */
    public fun with(field: String, value: JsonElement): RemoteRecipe = RemoteRecipe(JsonObject(json + (field to value)))

    /** A copy without [field]; every other field is kept unchanged. */
    public fun without(field: String): RemoteRecipe = RemoteRecipe(JsonObject(json - field))

    /** The recipe as JSON text, as it is stored locally and sent to the server. */
    public fun encode(): String = json.toString()

    /** Factory for recipes read from JSON text. */
    public companion object {
        /** Parses [text] as a recipe; returns `null` when it is not a JSON object. */
        public fun parse(text: String): RemoteRecipe? = parseObject(text)?.let(::RemoteRecipe)
    }
}

/**
 * One entry of the server's recipe list: enough to decide whether the full recipe must be downloaded.
 *
 * The server normalises the dates in this list (for example it adds a time zone), so [dateModified] is only
 * comparable with other stub values, not with the `dateModified` field of the full recipe.
 */
public data class RecipeStub(
    /** The server's id of the recipe. */
    val id: String,
    /** The recipe name, or `null` when the server reports none. */
    val name: String?,
    /** The modification date as the list reports it, or `null` when the recipe has none. */
    val dateModified: String?,
)

private val recipeJson = Json

internal fun parseElement(text: String): JsonElement? = try {
    recipeJson.parseToJsonElement(text)
} catch (ignored: SerializationException) {
    // Not JSON at all, for example an HTML error page; callers treat it as an unreadable answer.
    null
}

internal fun parseObject(text: String): JsonObject? = parseElement(text) as? JsonObject

/** Reads the stub list; entries without an id are skipped, because they cannot be synced. */
internal fun parseStubs(element: JsonElement?): List<RecipeStub>? = (element as? JsonArray)?.mapNotNull { entry ->
    val stub = entry as? JsonObject ?: return@mapNotNull null
    val id = stub["id"].textOrNull() ?: stub["recipe_id"].textOrNull() ?: return@mapNotNull null
    RecipeStub(id = id, name = stub["name"].textOrNull(), dateModified = stub["dateModified"].textOrNull())
}

/** The content of a JSON string or number, or `null` for anything else and for blank text. */
internal fun JsonElement?.textOrNull(): String? =
    (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.takeIf(String::isNotBlank)
