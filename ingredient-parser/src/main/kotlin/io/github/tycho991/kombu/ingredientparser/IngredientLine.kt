package io.github.tycho991.kombu.ingredientparser

import java.util.Locale

/**
 * One ingredient line as the parser understood it. Every case keeps the [original] text unchanged, so
 * nothing the user wrote is ever lost.
 */
public sealed interface IngredientLine {
    /** The line exactly as it was passed to the parser. */
    public val original: String

    /**
     * Returns this line with its quantity multiplied by [factor], for example `6/4` to go from four to six
     * servings. Units, names, notes, unmeasured lines and headings stay unchanged. Throws
     * [IllegalArgumentException] if [factor] is zero or negative.
     */
    public fun scaledBy(factor: Rational): IngredientLine {
        require(factor.isPositive) { "Scaling factor must be positive" }
        return when (this) {
            is Measured -> copy(amount = amount.scaledBy(factor))
            is Unmeasured, is Heading -> this
        }
    }

    /** A line that starts with a quantity, such as `2 EL Olivenöl` or `400g Mehl`. */
    public data class Measured(
        override val original: String,
        /** The quantity, a single value or a range. */
        public val amount: Amount,
        /** Whether the quantity was marked as approximate, for example with `ca.` or `~`. */
        public val approximate: Boolean,
        /** The kind of unit, or `null` for counted items such as `2 Zwiebeln`. */
        public val unit: IngredientUnit?,
        /** The unit as it was written, for example `EL` or `Prise(n)`; `null` if there is no unit. */
        public val unitText: String?,
        /** The ingredient name without unit and notes; may be empty, as in `1 Prise(n)`. */
        public val name: String,
        /** Text from parentheses and after the first comma, in the order of the line. */
        public val notes: List<String>,
        /** Where the quantity text, without an approximation word, stands in [original]. */
        public val quantityRange: IntRange,
    ) : IngredientLine {
        /**
         * Returns [original] with only the quantity text replaced by [amount], scaled by [factor] and
         * formatted for [locale]. The rest of the line, including units glued to the number, approximation
         * words and notes, stays exactly as written.
         */
        public fun displayText(locale: Locale, factor: Rational = Rational.ONE): String =
            original.replaceRange(quantityRange, amount.scaledBy(factor).format(locale))
    }

    /**
     * A line without a quantity at its start, such as `Salz & Pfeffer` or `Saft von 3 Orangen`. It is shown
     * as written and never scaled.
     */
    public data class Unmeasured(override val original: String) : IngredientLine

    /** A section heading such as `Für die Füllung:`. */
    public data class Heading(
        override val original: String,
        /** The heading without the trailing colon, for example `Für die Füllung`. */
        public val text: String,
    ) : IngredientLine
}
