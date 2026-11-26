package io.github.tycho991.kombu.cookbookclient

import io.github.tycho991.kombu.durationparser.IsoDuration
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration

/**
 * The recipe fields Kombu shows and edits, each stored under one top-level JSON key of the recipe.
 *
 * The source URL is shown but not edited, so it is not a field here; see [RecipeValues.sourceUrl].
 */
public enum class RecipeField(
    /** The schema.org key the field is stored under. */
    public val key: String,
) {
    /** The recipe name. */
    NAME("name"),

    /** The free-text description. */
    DESCRIPTION("description"),

    /** The single category. */
    CATEGORY("recipeCategory"),

    /** The keywords. */
    KEYWORDS("keywords"),

    /** The number of servings the recipe makes. */
    SERVINGS("recipeYield"),

    /** The preparation time. */
    PREP_TIME("prepTime"),

    /** The cooking time. */
    COOK_TIME("cookTime"),

    /** The total time. */
    TOTAL_TIME("totalTime"),

    /** The ingredient lines. */
    INGREDIENTS("recipeIngredient"),

    /** The steps. */
    STEPS("recipeInstructions"),
    ;

    /** Lookup of fields by their JSON key. */
    public companion object {
        private val byKey = entries.associateBy(RecipeField::key)

        /** The field stored under [key], or `null` when Kombu does not show that key as a field. */
        public fun ofKey(key: String): RecipeField? = byKey[key]
    }
}

/** A recipe time as stored: a readable ISO 8601 duration, or text Kombu cannot read and shows as it is. */
public sealed interface RecipeTime {
    /** A readable, non-zero duration. */
    public data class Known(
        /** The duration. */
        val duration: Duration,
    ) : RecipeTime

    /** A value that is not an ISO 8601 duration, kept and shown as stored. */
    public data class Raw(
        /** The stored text. */
        val text: String,
    ) : RecipeTime
}

/**
 * The typed values of a recipe's shown fields, read leniently by [RecipeFields.read].
 *
 * A field the recipe does not have, has empty, or stores in an unexpected type is `null` or an empty list.
 */
public data class RecipeValues(
    /** The name, or `null` when missing or blank. */
    val name: String? = null,
    /** The description, or `null` when missing or blank. */
    val description: String? = null,
    /** The category, or `null` when missing or blank. */
    val category: String? = null,
    /** The keywords, trimmed, without blank entries. */
    val keywords: List<String> = emptyList(),
    /** The servings, at least 1, or `null` when the recipe does not state them. */
    val servings: Int? = null,
    /** The preparation time, or `null` when missing, empty or zero. */
    val prepTime: RecipeTime? = null,
    /** The cooking time, or `null` when missing, empty or zero. */
    val cookTime: RecipeTime? = null,
    /** The total time, or `null` when missing, empty or zero. */
    val totalTime: RecipeTime? = null,
    /** The ingredient lines in order, without blank lines; headings are kept as lines. */
    val ingredients: List<String> = emptyList(),
    /** The step texts in order, without blank steps; sections are flattened. */
    val steps: List<String> = emptyList(),
    /** The source link, only when it is an `http` or `https` URL. */
    val sourceUrl: String? = null,
) {
    /** The value of [field], for comparing two snapshots field by field. */
    public operator fun get(field: RecipeField): Any? = when (field) {
        RecipeField.NAME -> name
        RecipeField.DESCRIPTION -> description
        RecipeField.CATEGORY -> category
        RecipeField.KEYWORDS -> keywords
        RecipeField.SERVINGS -> servings
        RecipeField.PREP_TIME -> prepTime
        RecipeField.COOK_TIME -> cookTime
        RecipeField.TOTAL_TIME -> totalTime
        RecipeField.INGREDIENTS -> ingredients
        RecipeField.STEPS -> steps
    }

    /** The fields whose values differ between this snapshot and [other]. */
    public fun differingFields(other: RecipeValues): Set<RecipeField> =
        RecipeField.entries.filterTo(mutableSetOf()) { this[it] != other[it] }
}

/**
 * Typed reading and single-key writing of the fields in [RecipeField] (design D3 of recipe-browse-edit).
 *
 * Reading is lenient, because real recipes store the same field in several forms. Writing replaces exactly one
 * key and leaves every other key of the recipe, and its form, untouched. A cleared category, servings or time
 * removes its key instead of writing an empty value.
 */
public object RecipeFields {
    private val LEADING_NUMBER = Regex("""^\s*(\d+)""")
    private val WEB_URL = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)

    /** Reads the typed values of [recipe]'s shown fields. */
    public fun read(recipe: RemoteRecipe): RecipeValues {
        val json = recipe.json
        return RecipeValues(
            name = recipe.name?.trim(),
            description = json["description"].stringOrNull(),
            category = recipe.category?.trim(),
            keywords = recipe.keywords,
            servings = servings(json[RecipeField.SERVINGS.key]),
            prepTime = time(json[RecipeField.PREP_TIME.key]),
            cookTime = time(json[RecipeField.COOK_TIME.key]),
            totalTime = time(json[RecipeField.TOTAL_TIME.key]),
            ingredients = lines(json[RecipeField.INGREDIENTS.key]),
            steps = steps(json[RecipeField.STEPS.key]),
            sourceUrl = json["url"].stringOrNull()?.trim()?.takeIf(WEB_URL::matches),
        )
    }

    /**
     * Writes [field] of [recipe] with its value from [values] and leaves every other key as it is.
     *
     * - Name and description are written as strings.
     * - Category, servings and times are removed when their value is `null`; servings are written as a
     *   number and times as ISO 8601 ([IsoDuration.format]), a [RecipeTime.Raw] as its text.
     * - Keywords are written as one comma-separated string without spaces, as Cookbook stores them.
     * - Ingredients and steps are written as lists of strings.
     */
    public fun write(recipe: RemoteRecipe, field: RecipeField, values: RecipeValues): RemoteRecipe = when (field) {
        RecipeField.NAME -> recipe.with(field.key, JsonPrimitive(values.name.orEmpty()))
        RecipeField.DESCRIPTION -> recipe.with(field.key, JsonPrimitive(values.description.orEmpty()))
        RecipeField.CATEGORY -> recipe.withOrWithout(field, values.category?.let(::JsonPrimitive))
        RecipeField.KEYWORDS -> recipe.with(field.key, JsonPrimitive(values.keywords.joinToString(",")))
        RecipeField.SERVINGS -> recipe.withOrWithout(field, values.servings?.let(::JsonPrimitive))
        RecipeField.PREP_TIME -> recipe.withOrWithout(field, values.prepTime?.toJson())
        RecipeField.COOK_TIME -> recipe.withOrWithout(field, values.cookTime?.toJson())
        RecipeField.TOTAL_TIME -> recipe.withOrWithout(field, values.totalTime?.toJson())
        RecipeField.INGREDIENTS -> recipe.with(field.key, JsonArray(values.ingredients.map(::JsonPrimitive)))
        RecipeField.STEPS -> recipe.with(field.key, JsonArray(values.steps.map(::JsonPrimitive)))
    }

    /** Writes every field in [fields] of [recipe] with its value from [values], see [write]. */
    public fun write(recipe: RemoteRecipe, fields: Set<RecipeField>, values: RecipeValues): RemoteRecipe =
        fields.fold(recipe) { current, field -> write(current, field, values) }

    private fun RemoteRecipe.withOrWithout(field: RecipeField, value: JsonElement?): RemoteRecipe =
        if (value == null) without(field.key) else with(field.key, value)

    private fun RecipeTime.toJson(): JsonPrimitive = when (this) {
        is RecipeTime.Known -> JsonPrimitive(IsoDuration.format(duration))
        is RecipeTime.Raw -> JsonPrimitive(text)
    }

    private fun servings(element: JsonElement?): Int? {
        val primitive = element as? JsonPrimitive ?: return null
        val digits = if (primitive.isString) {
            LEADING_NUMBER.find(primitive.content)?.groupValues?.get(1)
        } else {
            primitive.content.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toLong()?.toString()
        }
        return digits?.toIntOrNull()?.takeIf { it >= 1 }
    }

    private fun time(element: JsonElement?): RecipeTime? {
        val text = element.stringOrNull()?.trim() ?: return null
        val duration = IsoDuration.parse(text)
        return when {
            duration == null -> RecipeTime.Raw(text)
            duration == Duration.ZERO -> null
            else -> RecipeTime.Known(duration)
        }
    }

    /** A list of strings, or one string split into lines; blank entries and other types are dropped. */
    private fun lines(element: JsonElement?): List<String> = when (element) {
        is JsonArray -> element.mapNotNull { it.stringOrNull() }
        else -> element.stringOrNull()?.lines().orEmpty()
    }.map(String::trim).filter(String::isNotEmpty)

    /** Steps as lines of a string, a list of strings, `HowToStep` objects or `HowToSection` objects. */
    private fun steps(element: JsonElement?): List<String> = when (element) {
        is JsonArray -> element.flatMap(::stepItem)
        else -> element.stringOrNull()?.lines().orEmpty()
    }.map(String::trim).filter(String::isNotEmpty)

    private fun stepItem(item: JsonElement): List<String> = when (item) {
        is JsonObject -> when (val children = item["itemListElement"]) {
            // A HowToSection holds its steps in itemListElement; a HowToStep holds its text in text.
            is JsonArray -> children.flatMap(::stepItem)

            else -> listOfNotNull(item["text"].stringOrNull())
        }

        else -> listOfNotNull(item.stringOrNull())
    }

    /** The content of a non-blank JSON string; numbers, booleans, null, objects and arrays are absent. */
    private fun JsonElement?.stringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(String::isNotBlank)
}
