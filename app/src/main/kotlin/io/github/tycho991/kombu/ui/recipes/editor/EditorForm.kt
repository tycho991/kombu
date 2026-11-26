package io.github.tycho991.kombu.ui.recipes.editor

import io.github.tycho991.kombu.cookbookclient.RecipeTime
import io.github.tycho991.kombu.cookbookclient.RecipeValues
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private const val MINUTES_PER_HOUR = 60

/**
 * One recipe time in the editor: hours and minutes as typed, and the stored text of a time Kombu cannot read.
 * The [raw] text is kept until the user enters hours or minutes or clears it (spec "Servings and times").
 */
@Serializable
data class TimeInput(val hours: String = "", val minutes: String = "", val raw: String? = null) {
    /** Whether hours or minutes were entered. */
    val entered: Boolean get() = hours.isNotBlank() || minutes.isNotBlank()

    companion object {
        /** The input for a stored [time]. */
        fun of(time: RecipeTime?): TimeInput = when (time) {
            null -> TimeInput()

            is RecipeTime.Raw -> TimeInput(raw = time.text)

            is RecipeTime.Known -> {
                val total = time.duration.inWholeMinutes
                TimeInput(
                    hours = (total / MINUTES_PER_HOUR).takeIf { it > 0 }?.toString().orEmpty(),
                    minutes = (total % MINUTES_PER_HOUR).takeIf { it > 0 }?.toString().orEmpty(),
                )
            }
        }
    }
}

/** What the editor's fields hold, as typed; serialisable, so it survives process death (design D5). */
@Serializable
data class EditorForm(
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val keywords: List<String> = emptyList(),
    /** The keyword being typed; a comma moves it into [keywords]. */
    val keywordInput: String = "",
    val servings: String = "",
    val prepTime: TimeInput = TimeInput(),
    val cookTime: TimeInput = TimeInput(),
    val totalTime: TimeInput = TimeInput(),
    /** The ingredient lines, one per line. */
    val ingredients: String = "",
    val steps: List<String> = emptyList(),
) {
    /** Whether nothing was entered at all, as for a new recipe that was opened and left. */
    val isBlank: Boolean get() = this == EditorForm() || this == EditorForm(steps = listOf(""))

    /** [keywordInput] after typing [text]: complete keywords before a comma move into [keywords]. */
    fun typingKeyword(text: String): EditorForm {
        val parts = text.split(',')
        return if (parts.size == 1) {
            copy(keywordInput = text)
        } else {
            copy(keywords = mergeKeywords(keywords + parts.dropLast(1)), keywordInput = parts.last().trimStart())
        }
    }

    /** Adds [keyword], unless the recipe already has it ignoring case. */
    fun addingKeyword(keyword: String): EditorForm = copy(
        keywords = mergeKeywords(keywords + keyword),
        keywordInput = "",
    )

    companion object {
        /** The form for a recipe with [values]. */
        fun of(values: RecipeValues): EditorForm = EditorForm(
            name = values.name.orEmpty(),
            description = values.description.orEmpty(),
            category = values.category.orEmpty(),
            keywords = values.keywords,
            servings = values.servings?.toString().orEmpty(),
            prepTime = TimeInput.of(values.prepTime),
            cookTime = TimeInput.of(values.cookTime),
            totalTime = TimeInput.of(values.totalTime),
            ingredients = values.ingredients.joinToString("\n"),
            steps = values.steps,
        )

        /** Trimmed, without empty ones, merged ignoring case with the first spelling kept (spec "Keywords"). */
        fun mergeKeywords(keywords: List<String>): List<String> =
            keywords.map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase(Locale.ROOT) }
    }
}

/** Why the editor does not save (spec "Recipe name", "Servings and times"). */
enum class FormError {
    NAME_REQUIRED,
    NAME_TAKEN,
    SERVINGS_INVALID,
    PREP_TIME_INVALID,
    COOK_TIME_INVALID,
    TOTAL_TIME_INVALID,
}

/** The values of an [EditorForm] and the [errors] that prevent saving them; saving needs no errors. */
data class FormResult(val values: RecipeValues, val errors: Set<FormError>)

/**
 * Reads [form] into recipe values, normalised as the spec requires: trimmed text, keywords merged, empty
 * ingredient lines and steps dropped. Name uniqueness needs the local copy and is checked by the caller.
 */
fun EditorForm.toValues(): FormResult {
    val errors = mutableSetOf<FormError>()
    val name = name.trim()
    if (name.isEmpty()) errors += FormError.NAME_REQUIRED
    val servings = servings.trim().takeIf(String::isNotEmpty)?.let { text ->
        text.toIntOrNull()?.takeIf { it >= 1 } ?: null.also { errors += FormError.SERVINGS_INVALID }
    }
    val prep = prepTime.toTime { errors += FormError.PREP_TIME_INVALID }
    val cook = cookTime.toTime { errors += FormError.COOK_TIME_INVALID }
    val total = totalTime.toTime { errors += FormError.TOTAL_TIME_INVALID }
    val values = RecipeValues(
        name = name,
        description = description.trim().takeIf(String::isNotEmpty),
        category = category.trim().takeIf(String::isNotEmpty),
        keywords = EditorForm.mergeKeywords(keywords + keywordInput),
        servings = servings,
        prepTime = prep,
        cookTime = cook,
        totalTime = total,
        ingredients = ingredients.lines().map(String::trim).filter(String::isNotEmpty),
        steps = steps.map(String::trim).filter(String::isNotEmpty),
    )
    return FormResult(values, errors)
}

/** The time entered, the kept raw text, or `null` when cleared; [onInvalid] is called for bad numbers. */
private fun TimeInput.toTime(onInvalid: () -> Unit): RecipeTime? {
    if (!entered) return raw?.let(RecipeTime::Raw)
    val hours = hours.trim().ifEmpty { "0" }.toIntOrNull()?.takeIf { it >= 0 }
    val minutes = minutes.trim().ifEmpty { "0" }.toIntOrNull()?.takeIf { it in 0 until MINUTES_PER_HOUR }
    val duration = if (hours == null || minutes == null) null else hours.hours + minutes.minutes
    if (duration == null) onInvalid()
    return duration?.takeIf { it.inWholeMinutes > 0 }?.let(RecipeTime::Known)
}
