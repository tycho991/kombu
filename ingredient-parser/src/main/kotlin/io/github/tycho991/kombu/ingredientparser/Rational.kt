package io.github.tycho991.kombu.ingredientparser

/**
 * An exact fraction such as `3/2`, always reduced and with a positive denominator.
 *
 * Quantities use fractions instead of floating point, so that scaling `1/3` by three gives exactly `1`.
 * Arithmetic throws [ArithmeticException] if a result does not fit into a [Long]; quantities read by
 * [parse] are small enough that this cannot happen with realistic scaling factors.
 */
public class Rational private constructor(
    /** The numerator; negative for negative values. */
    public val numerator: Long,
    /** The denominator; always positive. */
    public val denominator: Long,
) : Comparable<Rational> {
    /** Whether this value is a whole number. */
    public val isWhole: Boolean get() = denominator == 1L

    /** Whether this value is greater than zero. */
    public val isPositive: Boolean get() = numerator > 0

    /** Returns the sum of this value and [other]. */
    public operator fun plus(other: Rational): Rational {
        val common = lcm(denominator, other.denominator)
        return of(
            Math.addExact(
                Math.multiplyExact(numerator, common / denominator),
                Math.multiplyExact(other.numerator, common / other.denominator),
            ),
            common,
        )
    }

    /** Returns the product of this value and [other]. */
    public operator fun times(other: Rational): Rational {
        // Cross-reduce first to keep the intermediate products small.
        val g1 = gcd(numerator, other.denominator)
        val g2 = gcd(other.numerator, denominator)
        return of(
            Math.multiplyExact(numerator / g1, other.numerator / g2),
            Math.multiplyExact(denominator / g2, other.denominator / g1),
        )
    }

    /** Returns this value divided by [other]; throws [ArithmeticException] if [other] is zero. */
    public operator fun div(other: Rational): Rational {
        if (other.numerator == 0L) throw ArithmeticException("Division by zero")
        return this * of(other.denominator, other.numerator)
    }

    /** Returns this value as a [Double], for display decisions that do not need exactness. */
    public fun toDouble(): Double = numerator.toDouble() / denominator

    override fun compareTo(other: Rational): Int =
        Math.multiplyExact(numerator, other.denominator).compareTo(Math.multiplyExact(other.numerator, denominator))

    override fun equals(other: Any?): Boolean =
        other is Rational && other.numerator == numerator && other.denominator == denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    /** Returns `n` for whole numbers and `n/d` otherwise, for logs and tests; not meant for display. */
    override fun toString(): String = if (isWhole) "$numerator" else "$numerator/$denominator"

    /** Ways to create a [Rational]. */
    public companion object {
        /** Zero. */
        public val ZERO: Rational = Rational(0, 1)

        /** One. */
        public val ONE: Rational = Rational(1, 1)

        private const val MAX_INTEGER_DIGITS = 6
        private const val MAX_DECIMAL_DIGITS = 3
        private const val DECIMAL_BASE = 10L

        private val UNICODE_FRACTIONS: Map<Char, Pair<Long, Long>> =
            mapOf(
                '½' to (1L to 2L),
                '⅓' to (1L to 3L),
                '⅔' to (2L to 3L),
                '¼' to (1L to 4L),
                '¾' to (3L to 4L),
                '⅕' to (1L to 5L),
                '⅖' to (2L to 5L),
                '⅗' to (3L to 5L),
                '⅘' to (4L to 5L),
                '⅙' to (1L to 6L),
                '⅚' to (5L to 6L),
                '⅐' to (1L to 7L),
                '⅛' to (1L to 8L),
                '⅜' to (3L to 8L),
                '⅝' to (5L to 8L),
                '⅞' to (7L to 8L),
                '⅑' to (1L to 9L),
                '⅒' to (1L to 10L),
            )

        /** The Unicode fraction characters that [parse] understands, for building patterns. */
        internal val unicodeFractionCharacters: String = UNICODE_FRACTIONS.keys.joinToString("")

        private const val DIGITS = "\\d{1,$MAX_INTEGER_DIGITS}"
        private val WHOLE = Regex(DIGITS)
        private val DECIMAL = Regex("($DIGITS)[.,](\\d{1,$MAX_DECIMAL_DIGITS})")
        private val FRACTION = Regex("($DIGITS)\\s*/\\s*($DIGITS)")
        private val MIXED = Regex("($DIGITS)\\s+($DIGITS)\\s*/\\s*($DIGITS)")
        private val UNICODE = Regex("(?:($DIGITS)\\s*)?([${UNICODE_FRACTIONS.keys.joinToString("")}])")

        /** Returns the reduced fraction [numerator]/[denominator]; throws if [denominator] is zero. */
        public fun of(numerator: Long, denominator: Long = 1): Rational {
            require(denominator != 0L) { "Denominator must not be zero" }
            val sign = if (denominator < 0) -1 else 1
            val divisor = gcd(numerator, denominator)
            return Rational(sign * numerator / divisor, sign * denominator / divisor)
        }

        /**
         * Reads a quantity written as a whole number (`12`), a decimal with a point or a comma (`1.5`, `1,5`),
         * an ASCII fraction (`1/2`), a mixed number (`1 1/2`) or a Unicode fraction, alone or after a whole
         * number (`½`, `1½`). Returns `null` for anything else, for a zero denominator, and for numbers with
         * more than six integer digits or more than three decimal digits.
         */
        public fun parse(text: String): Rational? {
            val input = text.trim()
            return WHOLE.matchEntire(input)?.let { of(it.value.toLong()) }
                ?: DECIMAL.matchEntire(input)?.let(::decimal)
                ?: FRACTION.matchEntire(input)?.let { fraction(null, it.groupValues[1], it.groupValues[2]) }
                ?: MIXED.matchEntire(input)?.destructured?.let { (whole, top, bottom) -> fraction(whole, top, bottom) }
                ?: UNICODE.matchEntire(input)?.let(::unicode)
        }

        private fun decimal(match: MatchResult): Rational {
            val (whole, fraction) = match.destructured
            var scale = 1L
            repeat(fraction.length) { scale *= DECIMAL_BASE }
            return of(whole.toLong() * scale + fraction.toLong(), scale)
        }

        private fun fraction(whole: String?, numerator: String, denominator: String): Rational? {
            val bottom = denominator.toLong()
            if (bottom == 0L) return null
            val part = of(numerator.toLong(), bottom)
            return if (whole == null) part else of(whole.toLong()) + part
        }

        private fun unicode(match: MatchResult): Rational {
            val (top, bottom) = UNICODE_FRACTIONS.getValue(match.groupValues[2].single())
            val part = of(top, bottom)
            val whole = match.groupValues[1]
            return if (whole.isEmpty()) part else of(whole.toLong()) + part
        }

        private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) maxOf(kotlin.math.abs(a), 1L) else gcd(b, a % b)

        private fun lcm(a: Long, b: Long): Long = Math.multiplyExact(a / gcd(a, b), b)
    }
}
