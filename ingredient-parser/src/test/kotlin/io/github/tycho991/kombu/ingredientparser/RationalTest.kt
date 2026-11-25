package io.github.tycho991.kombu.ingredientparser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class RationalTest {
    @Nested
    inner class Construction {
        @Test
        fun `reduces the fraction`() {
            val value = Rational.of(6, 8)
            assertEquals(3, value.numerator)
            assertEquals(4, value.denominator)
        }

        @Test
        fun `keeps the denominator positive`() {
            assertEquals(Rational.of(-1, 2), Rational.of(1, -2))
            assertEquals(2, Rational.of(1, -2).denominator)
        }

        @Test
        fun `zero has denominator one`() {
            assertEquals(Rational.ZERO, Rational.of(0, 5))
            assertEquals(1, Rational.of(0, 5).denominator)
        }

        @Test
        fun `rejects a zero denominator`() {
            assertThrows<IllegalArgumentException> { Rational.of(1, 0) }
        }

        @Test
        fun `describes itself for logs`() {
            assertEquals("3/2", Rational.of(3, 2).toString())
            assertEquals("4", Rational.of(8, 2).toString())
        }

        @Test
        fun `knows whole and positive values`() {
            assertTrue(Rational.of(4, 2).isWhole)
            assertFalse(Rational.of(1, 2).isWhole)
            assertTrue(Rational.ONE.isPositive)
            assertFalse(Rational.ZERO.isPositive)
            assertFalse(Rational.of(-1, 3).isPositive)
        }
    }

    @Nested
    inner class Arithmetic {
        @Test
        fun `one third times three is exactly one`() {
            assertEquals(Rational.ONE, Rational.of(1, 3) * Rational.of(3))
        }

        @Test
        fun `adds fractions with different denominators`() {
            assertEquals(Rational.of(5, 6), Rational.of(1, 2) + Rational.of(1, 3))
        }

        @Test
        fun `divides`() {
            assertEquals(Rational.of(3, 2), Rational.of(6) / Rational.of(4))
        }

        @Test
        fun `rejects division by zero`() {
            assertThrows<ArithmeticException> { Rational.ONE / Rational.ZERO }
        }

        @Test
        fun `throws instead of overflowing`() {
            val huge = Rational.of(Long.MAX_VALUE / 2)
            assertThrows<ArithmeticException> { huge * Rational.of(3) }
            assertThrows<ArithmeticException> { huge + huge + huge }
        }

        @Test
        fun `compares by value`() {
            assertTrue(Rational.of(1, 3) < Rational.of(1, 2))
            assertEquals(0, Rational.of(2, 4).compareTo(Rational.of(1, 2)))
        }

        @Test
        fun `equality and hash follow the reduced value`() {
            assertEquals(Rational.of(2, 4), Rational.of(1, 2))
            assertEquals(Rational.of(2, 4).hashCode(), Rational.of(1, 2).hashCode())
            assertFalse(Rational.of(1, 2).equals("1/2"))
        }

        @Test
        fun `converts to double`() {
            assertEquals(0.125, Rational.of(1, 8).toDouble())
        }
    }

    @Nested
    inner class Parsing {
        @ParameterizedTest(name = "{0} is {1}/{2}")
        @CsvSource(
            delimiter = ';',
            value = [
                "12;12;1",
                "0.125;1;8",
                "1,5;3;2",
                "1.50;3;2",
                "1/2;1;2",
                "3 / 4;3;4",
                "1 1/2;3;2",
                "2 3/4;11;4",
                "½;1;2",
                "1½;3;2",
                "1 ½;3;2",
                "⅛;1;8",
                "⅒;1;10",
                "  4  ;4;1",
                "999999;999999;1",
                "0;0;1",
            ],
        )
        fun `reads quantity notations exactly`(text: String, numerator: Long, denominator: Long) {
            assertEquals(Rational.of(numerator, denominator), Rational.parse(text))
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "",
                "abc",
                "1/0",
                "1 1/0",
                "1234567",
                "1.2345",
                "1,",
                ".5",
                "1-2",
                "1 2",
                "½½",
                "-1",
            ],
        )
        fun `rejects anything else`(text: String) {
            assertNull(Rational.parse(text))
        }
    }
}
