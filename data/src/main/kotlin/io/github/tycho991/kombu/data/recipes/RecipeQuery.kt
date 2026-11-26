package io.github.tycho991.kombu.data.recipes

import io.github.tycho991.kombu.cookbookclient.RecipeFields
import java.text.Collator
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

/** How the recipe list is sorted (spec "Sort order"). */
enum class SortOrder {
    /** By name, in the alphabetical order of the app language. */
    NAME,

    /** By the last change here or on the server, newest first. */
    LAST_CHANGE,
}

/**
 * What the recipe list shows (design D7): search text, filters and sort order.
 *
 * @property search whitespace-separated terms; each must occur in the name, category, keywords or ingredients.
 * @property category only recipes of this category, or `null` for all.
 * @property keywords only recipes that have all of these keywords.
 * @property attentionOnly only recipes in conflict or with a refused upload.
 */
data class ListState(
    val search: String = "",
    val category: String? = null,
    val keywords: Set<String> = emptySet(),
    val attentionOnly: Boolean = false,
    val sort: SortOrder = SortOrder.NAME,
) {
    /** Whether a search or a filter narrows the list. */
    val isNarrowed: Boolean get() = search.isNotBlank() || category != null || keywords.isNotEmpty() || attentionOnly
}

/** Whether the recipe needs the user: it is in conflict, or the server refused its upload. */
val LocalRecipe.needsAttention: Boolean get() = state == RecipeState.CONFLICT || uploadError != null

/**
 * Search, filters and sorting of the recipe list in memory (design D7 of recipe-browse-edit).
 *
 * [index] normalises the searchable texts of every recipe once per list emission, so [query] stays cheap
 * while the user types. Matching ignores case and accents and treats `ß` as `ss`.
 *
 * @param locale the app language, for the alphabetical order.
 */
class RecipeQuery(locale: Locale) {
    private val collator: Collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }

    /** The recipes with their normalised texts, ready for [query]. */
    class Index internal constructor(internal val entries: List<Entry>)

    internal class Entry(
        val recipe: LocalRecipe,
        val name: String,
        val texts: List<String>,
        val category: String?,
        val keywords: Set<String>,
        val changedAt: Long?,
    )

    /** Builds the search index of [recipes]. */
    fun index(recipes: List<LocalRecipe>): Index = Index(
        recipes.map { recipe ->
            val values = recipe.shown?.let(RecipeFields::read)
            val name = values?.name.orEmpty()
            val category = values?.category
            val keywords = values?.keywords.orEmpty()
            Entry(
                recipe = recipe,
                name = name,
                texts = (listOf(name) + listOfNotNull(category) + keywords + values?.ingredients.orEmpty())
                    .map(::normalise),
                category = category?.let(::normalise),
                keywords = keywords.mapTo(mutableSetOf(), ::normalise),
                changedAt = changedAt(recipe),
            )
        },
    )

    /** The recipes of [index] that [state] shows, in its sort order. */
    fun query(index: Index, state: ListState): List<LocalRecipe> {
        val terms = normalise(state.search).split(WHITESPACE).filter(String::isNotEmpty)
        val category = state.category?.let(::normalise)
        val keywords = state.keywords.map(::normalise)
        val byName = compareBy(collator) { entry: Entry -> entry.name }
        val order = when (state.sort) {
            SortOrder.NAME -> byName
            SortOrder.LAST_CHANGE -> compareBy<Entry, Long?>(nullsLast(reverseOrder())) { it.changedAt }.then(byName)
        }
        return index.entries
            .asSequence()
            .filter { entry -> terms.all { term -> entry.texts.any { term in it } } }
            .filter { category == null || it.category == category }
            .filter { it.keywords.containsAll(keywords) }
            .filter { !state.attentionOnly || it.recipe.needsAttention }
            .sortedWith(order)
            .map(Entry::recipe)
            .toList()
    }

    /** The distinct categories of [recipes], sorted alphabetically; spellings that differ only in case merge. */
    fun categories(recipes: List<LocalRecipe>): List<String> =
        distinctSorted(recipes.mapNotNull { it.shown?.let(RecipeFields::read)?.category })

    /** The distinct keywords of [recipes], sorted alphabetically; spellings that differ only in case merge. */
    fun keywords(recipes: List<LocalRecipe>): List<String> =
        distinctSorted(recipes.flatMap { it.shown?.let(RecipeFields::read)?.keywords.orEmpty() })

    private fun distinctSorted(values: List<String>): List<String> =
        values.map(String::trim).distinctBy { it.lowercase(Locale.ROOT) }.sortedWith(collator)

    internal companion object {
        private val WHITESPACE = Regex("""\s+""")
        private val COMBINING_MARKS = Regex("""\p{M}+""")

        /** A local `dateModified` without zone, as Cookbook writes it into the recipe: `2024-01-01 10:00:00`. */
        private val LOCAL_DATE: DateTimeFormatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .optionalStart().appendLiteral('T').optionalEnd()
            .optionalStart().appendLiteral(' ').optionalEnd()
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .toFormatter(Locale.ROOT)

        /** A zoned date as the recipe list reports it: `2024-01-01T10:00:00+0000` or `+00:00`. */
        private val ZONED_DATES: List<DateTimeFormatter> = listOf(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX", Locale.ROOT),
        )

        /** Lower case, without accents, `ß` as `ss`. */
        fun normalise(text: String): String {
            val lower = text.lowercase(Locale.ROOT).replace("ß", "ss")
            return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        }

        /** The later of the local change and the shown version's `dateModified`, in epoch milliseconds. */
        fun changedAt(recipe: LocalRecipe): Long? {
            val modified = recipe.shown?.dateModified?.let(::parseDate)
            return listOfNotNull(recipe.localChangeAt, modified).maxOrNull()
        }

        /** Parses a `dateModified`; a date without zone counts as UTC. `null` when it cannot be read. */
        fun parseDate(text: String): Long? {
            val trimmed = text.trim()
            val zoned = ZONED_DATES.firstNotNullOfOrNull { format ->
                try {
                    OffsetDateTime.parse(trimmed, format).toInstant().toEpochMilli()
                } catch (ignored: DateTimeParseException) {
                    // Try the next form; unreadable dates sort last.
                    null
                }
            }
            return zoned ?: try {
                LocalDateTime.parse(trimmed, LOCAL_DATE).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (ignored: DateTimeParseException) {
                // Neither form; the caller sorts the recipe last.
                null
            }
        }
    }
}
