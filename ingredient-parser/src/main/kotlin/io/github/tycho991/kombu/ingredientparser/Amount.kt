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
     * Formats this amount for display: whole numbers as they are, values close to a multiple of 1/2, 1/3,
     * 1/4 or 1/8 as a fraction or mixed number (`1/3`, `2 1/4`), and anything else as a decimal with at most
     * two places and the decimal separator of [locale]. A range is shown as both ends joined by an en dash.
     */
    public fun format(locale: Locale): String = if (isRange) {
        "${QuantityFormat.format(min, locale)}$RANGE_DASH${QuantityFormat.format(max, locale)}"
    } else {
        QuantityFormat.format(min, locale)
    }

    private companion object {
        const val RANGE_DASH = "–"
    }
}

/** Kitchen-friendly formatting of a single quantity (design D6). */
internal object QuantityFormat {
    /** Denominators tried in this order; the first one that fits wins, so simpler fractions are preferred. */
    private val DENOMINATORS = listOf(2L, 3L, 4L, 8L)

    /** Largest relative distance to a fraction that is still shown as that fraction. */
    private const val TOLERANCE = 0.02
    private const val DECIMAL_PLACES = 2

    fun format(value: Rational, locale: Locale): String {
        if (value.isWhole) return value.numerator.toString()
        val exact = value.toDouble()
        return DENOMINATORS.firstNotNullOfOrNull { denominator ->
            val multiples = (exact * denominator).roundToLong()
            val close = multiples > 0 && abs(multiples.toDouble() / denominator - exact) <= TOLERANCE * exact
            if (close) fraction(multiples, denominator) else null
        } ?: decimal(value, locale)
    }

    private fun fraction(multiples: Long, denominator: Long): String {
        val reduced = Rational.of(multiples, denominator)
        val whole = reduced.numerator / reduced.denominator
        val rest = reduced.numerator % reduced.denominator
        return when {
            rest == 0L -> "$whole"
            whole == 0L -> "$rest/${reduced.denominator}"
            else -> "$whole $rest/${reduced.denominator}"
        }
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
