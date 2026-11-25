// Quantity handling follows @magrinj/parse-ingredients 1.0.0 (MIT, Copyright (c) 2021 Jérémy Magrin),
// src/utils/findQuantityAndConvertIfUnicode.ts; see NOTICE in this module. Unlike upstream, Kombu reads a
// quantity only at the start of the line, accepts a decimal comma and approximation words, and keeps
// quantities as exact fractions.
package io.github.tycho991.kombu.ingredientparser

/** A quantity found at the start of a line. */
internal class QuantityMatch(
    val amount: Amount,
    val approximate: Boolean,
    /** Position of the quantity text, without approximation word, in the searched text. */
    val range: IntRange,
    /** Position right after the quantity in the searched text. */
    val end: Int,
)

/** Reads the quantity at the start of a line (design D5, step 2). */
internal object QuantityReader {
    private const val NUMBER = "\\d{1,6}"
    private val FRACTIONS = Rational.unicodeFractionCharacters
    private val SINGLE =
        "(?:$NUMBER\\s+$NUMBER\\s*/\\s*$NUMBER" +
            "|$NUMBER\\s*/\\s*$NUMBER" +
            "|(?:$NUMBER\\s*)?[$FRACTIONS]" +
            "|$NUMBER[.,]\\d{1,3}" +
            "|$NUMBER)" +
            // A quantity must not run into more digits, as in `1234567` or `1.2345`.
            "(?!\\d|[.,]\\d)"
    private const val RANGE_SEPARATOR = "(?:\\s*[-–]\\s*|\\s+(?:bis|to)\\s+)"
    private val PATTERN =
        Regex(
            "(?<approx>(?:(?:ca|circa|etwa|about|approx)\\.?\\s*|~\\s*)(?=[\\d$FRACTIONS]))?" +
                "(?<min>$SINGLE)(?:$RANGE_SEPARATOR(?<max>$SINGLE))?",
            RegexOption.IGNORE_CASE,
        )

    /** Returns the quantity that starts at [start] in [text], or `null` if there is none. */
    fun read(text: String, start: Int): QuantityMatch? {
        val match = PATTERN.matchAt(text, start) ?: return null
        val groups = match.groups
        val min = groups["min"]?.value?.let(Rational::parse)
        val max = groups["max"]?.value?.let(Rational::parse) ?: min
        val valid = min != null && max != null && min <= max
        return if (valid) {
            val approx = groups["approx"]
            val quantityStart = approx?.range?.last?.plus(1) ?: match.range.first
            QuantityMatch(
                amount = Amount(min, max),
                approximate = approx != null,
                range = quantityStart..match.range.last,
                end = match.range.last + 1,
            )
        } else {
            null
        }
    }
}
