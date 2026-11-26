package io.github.tycho991.kombu.ingredientparser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.Locale

class AmountTest {
    private fun r(numerator: Long, denominator: Long = 1) = Rational.of(numerator, denominator)

    @Nested
    inner class Values {
        @Test
        fun `a single value is not a range`() {
            val amount = Amount(r(2))
            assertEquals(amount.min, amount.max)
            assertFalse(amount.isRange)
        }

        @Test
        fun `two different ends form a range`() {
            assertTrue(Amount(r(2), r(3)).isRange)
        }

        @Test
        fun `rejects a reversed range`() {
            assertThrows<IllegalArgumentException> { Amount(r(3), r(2)) }
        }
    }

    @Nested
    inner class Scaling {
        @Test
        fun `doubling a third gives exactly two thirds`() {
            assertEquals(Amount(r(2, 3)), Amount(r(1, 3)).scaledBy(r(2)))
        }

        @Test
        fun `four to six servings`() {
            assertEquals(Amount(r(9, 2)), Amount(r(3)).scaledBy(r(6, 4)))
        }

        @Test
        fun `scales both ends of a range`() {
            assertEquals(Amount(r(4), r(6)), Amount(r(2), r(3)).scaledBy(r(2)))
        }

        @Test
        fun `does not change the input`() {
            val original = Amount(r(3))
            original.scaledBy(r(2))
            assertEquals(Amount(r(3)), original)
        }

        @Test
        fun `rejects zero and negative factors`() {
            assertThrows<IllegalArgumentException> { Amount(r(1)).scaledBy(Rational.ZERO) }
            assertThrows<IllegalArgumentException> { Amount(r(1)).scaledBy(r(-1)) }
        }
    }

    @Nested
    inner class Formatting {
        @ParameterizedTest(name = "{0}/{1} is shown as {2}")
        @CsvSource(
            delimiter = ';',
            value = [
                "400;1;400",
                "0;1;0",
                "33;100;⅓",
                "1;2;½",
                "2;3;⅔",
                "3;4;¾",
                "1;8;⅛",
                "9;4;2¼",
                "3;2;1½",
                "201;100;2",
                "1;10;0.1",
                "115;100;1.15",
                "1071;1000;1.07",
                "1234;1000;1¼",
                "1;7;0.14",
                "5;3;1⅔",
                "3;8;⅜",
                "5;8;⅝",
                "7;8;⅞",
                "17;8;2⅛",
            ],
        )
        fun `english display`(numerator: Long, denominator: Long, expected: String) {
            assertEquals(expected, Amount(r(numerator, denominator)).format(Locale.ENGLISH))
        }

        @Test
        fun `german display uses the decimal comma`() {
            assertEquals("1,15", Amount(r(115, 100)).format(Locale.GERMAN))
        }

        @Test
        fun `fractions look the same in every locale`() {
            assertEquals("2¼", Amount(r(9, 4)).format(Locale.GERMAN))
            assertEquals("2¼", Amount(r(9, 4)).format(Locale.ENGLISH))
        }

        @Test
        fun `the decimal style never uses fractions`() {
            assertEquals("1,5", Amount(r(3, 2)).format(Locale.GERMAN, QuantityStyle.DECIMAL))
            assertEquals("0.75", Amount(r(3, 4)).format(Locale.ENGLISH, QuantityStyle.DECIMAL))
            assertEquals("0,33", Amount(r(1, 3)).format(Locale.GERMAN, QuantityStyle.DECIMAL))
            assertEquals("2", Amount(r(2)).format(Locale.GERMAN, QuantityStyle.DECIMAL))
            assertEquals("0,5–1", Amount(r(1, 2), r(1)).format(Locale.GERMAN, QuantityStyle.DECIMAL))
        }

        @Test
        fun `metric units use decimals, every other unit fractions`() {
            val metric = setOf(
                IngredientUnit.MILLIGRAM,
                IngredientUnit.GRAM,
                IngredientUnit.KILOGRAM,
                IngredientUnit.MILLILITER,
                IngredientUnit.CENTILITER,
                IngredientUnit.LITER,
            )
            IngredientUnit.entries.forEach { unit ->
                val expected = if (unit in metric) QuantityStyle.DECIMAL else QuantityStyle.FRACTION
                assertEquals(expected, QuantityStyle.of(unit), unit.name)
                assertEquals(unit in metric, unit.isMetric, unit.name)
            }
            assertEquals(QuantityStyle.FRACTION, QuantityStyle.of(null))
        }

        @Test
        fun `a range shows both ends with an en dash`() {
            assertEquals("2–3", Amount(r(2), r(3)).format(Locale.GERMAN))
            assertEquals("½–1", Amount(r(1, 2), r(1)).format(Locale.ENGLISH))
        }
    }
}
