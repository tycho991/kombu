package io.github.tycho991.kombu.ingredientparser

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The quantity of a measured ingredient line: a single value, or a range from [min] to [max] such as `2-3`.
 */
public data class Amount(
    /** The smallest quantity; equal to [max] unless the line gives a range. */
    public val min: Rational,
    /** The largest quantity. */
    public val max: Rational = min,
) {
    init {
        require(min <= max) { "min must not be greater than max" }
    }

    /** Whether this amount is a range with two different ends. */
    public val isRange: Boolean get() = min != max

    /**
     * Returns this amount multiplied by [factor], for example `6/4` to go from four to six servings.
     * Throws [IllegalArgumentException] if [factor] is zero or negative.
     */
    public fun scaledBy(factor: Rational): Amount {
        require(factor.isPositive) { "Scaling factor must be positive" }
        return Amount(min * factor, max * factor)
    }

    /**
     * Formats this amount for display in [style]. Whole numbers are shown as they are. With
     * [QuantityStyle.FRACTION], values close to a multiple of 1/2, 1/3, 1/4 or 1/8 are shown with a fraction
     * character (`⅓`, `2¼`). Anything else is a decimal with at most two places and the decimal separator of
     * [locale]. A range is shown as both ends joined by an en dash.
     */
    public fun format(locale: Locale, style: QuantityStyle = QuantityStyle.FRACTION): String = if (isRange) {
        "${QuantityFormat.format(min, locale, style)}$RANGE_DASH${QuantityFormat.format(max, locale, style)}"
    } else {
        QuantityFormat.format(min, locale, style)
    }

    private companion object {
        const val RANGE_DASH = "–"
    }
}

/** How a quantity is written: the kitchen's form for its unit (spec ingredient-parsing). */
public enum class QuantityStyle {
    /** Fractions with fraction characters where they fit (`1½`, `¾`), decimals otherwise; for spoons and pieces. */
    FRACTION,

    /** Decimals (`1,5`, `0,75`); for metric units. */
    DECIMAL,
    ;

    /** Style lookup by unit. */
    public companion object {
        /** The style for quantities in [unit]: decimals for metric units, fractions otherwise. */
        public fun of(unit: IngredientUnit?): QuantityStyle = if (unit?.isMetric == true) DECIMAL else FRACTION
    }
}

/** Kitchen-friendly formatting of a single quantity (design D6). */
internal object QuantityFormat {
    /** Denominators tried in this order; the first one that fits wins, so simpler fractions are preferred. */
    private val DENOMINATORS = listOf(2L, 3L, 4L, 8L)

    /** Largest relative distance to a fraction that is still shown as that fraction. */
    private const val TOLERANCE = 0.02
    private const val DECIMAL_PLACES = 2

    /** The fraction characters by reduced fraction; every reduced fraction of the denominators has one. */
    private val GLYPHS = mapOf(
        "1/2" to "½",
        "1/3" to "⅓",
        "2/3" to "⅔",
        "1/4" to "¼",
        "3/4" to "¾",
        "1/8" to "⅛",
        "3/8" to "⅜",
        "5/8" to "⅝",
        "7/8" to "⅞",
    )

    fun format(value: Rational, locale: Locale, style: QuantityStyle = QuantityStyle.FRACTION): String = when {
        value.isWhole -> value.numerator.toString()
        style == QuantityStyle.DECIMAL -> decimal(value, locale)
        else -> snappedFraction(value) ?: decimal(value, locale)
    }

    /** [value] as a fraction or mixed number with a fraction character, or `null` when none is close enough. */
    private fun snappedFraction(value: Rational): String? {
        val exact = value.toDouble()
        return DENOMINATORS.firstNotNullOfOrNull { denominator ->
            val multiples = (exact * denominator).roundToLong()
            val close = multiples > 0 && abs(multiples.toDouble() / denominator - exact) <= TOLERANCE * exact
            if (close) fraction(multiples, denominator) else null
        }
    }

    private fun fraction(multiples: Long, denominator: Long): String {
        val reduced = Rational.of(multiples, denominator)
        val whole = reduced.numerator / reduced.denominator
        val rest = reduced.numerator % reduced.denominator
        // A value that snaps to a whole number, such as 2.01, has no fractional part left.
        if (rest == 0L) return "$whole"
        val glyph = GLYPHS.getValue("$rest/${reduced.denominator}")
        return if (whole == 0L) glyph else "$whole$glyph"
    }

    private fun decimal(value: Rational, locale: Locale): String {
        val rounded =
            BigDecimal
                .valueOf(value.numerator)
                .divide(BigDecimal.valueOf(value.denominator), DECIMAL_PLACES, RoundingMode.HALF_UP)
        val pattern = DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale))
        return pattern.format(rounded)
    }
}
