// Ported from @magrinj/parse-ingredients 1.0.0 (MIT, Copyright (c) 2021 Jérémy Magrin),
// src/__tests__/index.test.ts, the "ingredient parser" tests; see NOTICE in this module. The tests for
// combine and pretty are not ported, because Kombu does not port those functions.
//
// Kombu deliberately changes some results; the affected tests are adapted and say why:
// - quantities are exact fractions instead of rounded decimal strings (1/3 instead of "0.333");
// - units are identifiers instead of singular or plural display words ("cups" becomes CUP);
// - parenthesised text is a note instead of being appended to the ingredient name;
// - size words such as "large" are part of the name, not units;
// - a line without a quantity is unmeasured instead of a record full of nulls.
package io.github.tycho991.kombu.ingredientparser

import io.github.tycho991.kombu.ingredientparser.IngredientLine.Measured
import io.github.tycho991.kombu.ingredientparser.IngredientLine.Unmeasured
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class UpstreamParseTest {
    private fun parse(line: String) = IngredientParser.parse(line) as Measured

    private fun r(numerator: Long, denominator: Long = 1) = Rational.of(numerator, denominator)

    @Nested
    inner class TranslatesTheQuantity {
        // Adapted: exact fractions instead of rounded decimal strings.
        @ParameterizedTest(name = "{0}")
        @CsvSource(
            delimiter = ';',
            value = [
                "1 teaspoon water;1;1",
                "1.5 teaspoon water;3;2",
                "1 1/2 teaspoon water;3;2",
                "1/3 cup water;1;3",
                "1/2 teaspoon water;1;2",
                "10 1/2 teaspoon water;21;2",
                "about 1/2 teaspoon water;1;2",
            ],
        )
        fun quantity(line: String, numerator: Long, denominator: Long) {
            assertEquals(Amount(r(numerator, denominator)), parse(line).amount)
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
            delimiter = ';',
            value = ["10-20 teaspoon water", "10 - 20 teaspoon water", "10 to 20 teaspoon water"],
        )
        fun `quantity range`(line: String) {
            assertEquals(Amount(r(10), r(20)), parse(line).amount)
        }

        // Adapted: exact fractions instead of decimals truncated to three places ("0.142").
        @ParameterizedTest(name = "{0}")
        @CsvSource(
            delimiter = ';',
            value = [
                "¼;1;4", "½;1;2", "¾;3;4", "⅐;1;7", "⅑;1;9", "⅒;1;10", "⅓;1;3", "⅔;2;3", "⅕;1;5",
                "⅖;2;5", "⅗;3;5", "⅘;4;5", "⅙;1;6", "⅚;5;6", "⅛;1;8", "⅜;3;8", "⅝;5;8", "⅞;7;8",
            ],
        )
        fun `unicode fractions`(fraction: String, numerator: Long, denominator: Long) {
            assertEquals(Amount(r(numerator, denominator)), parse("$fraction teaspoon water").amount)
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
            delimiter = ';',
            value = [
                "1¼;5;4", "2½;5;2", "3¾;15;4", "4⅐;29;7", "5⅑;46;9", "6⅒;61;10", "7⅓;22;3", "8⅔;26;3",
                "9⅕;46;5", "10⅖;52;5", "11⅗;58;5", "12⅘;64;5", "13⅙;79;6", "14⅚;89;6", "15⅛;121;8",
                "16⅜;131;8", "17⅝;141;8", "18⅞;151;8",
            ],
        )
        fun `mixed unicode fractions`(mixed: String, numerator: Long, denominator: Long) {
            assertEquals(Amount(r(numerator, denominator)), parse("$mixed teaspoon water").amount)
        }

        @Test
        fun `150 cl of milk`() {
            val line = parse("150 cl of milk")
            assertEquals(Amount(r(150)), line.amount)
            assertEquals(IngredientUnit.CENTILITER, line.unit)
            assertEquals("milk", line.name)
        }

        @Test
        fun `does not freak out if a strange unicode character is present`() {
            val line = parse("1/3 cup confectioners’ sugar")
            assertEquals(Amount(r(1, 3)), line.amount)
            assertEquals(IngredientUnit.CUP, line.unit)
            assertEquals("confectioners’ sugar", line.name)
        }
    }

    @Nested
    inner class TranslatesTheLiteralUnits {
        @ParameterizedTest(name = "{0}")
        @CsvSource(
            delimiter = ';',
            value = [
                "1 cup water;CUP",
                "1 gallon water;GALLON",
                "1 ounce water;OUNCE",
                "1 pint water;PINT",
                "1 pound water;POUND",
                "1 quart water;QUART",
                "1 tablespoon water;TABLESPOON",
                "1 teaspoon water;TEASPOON",
                "1 gram water;GRAM",
                "1 kilogram water;KILOGRAM",
                "1 liter water;LITER",
                "1 milligram water;MILLIGRAM",
                "1 milliliter water;MILLILITER",
                "1 clove garlic;CLOVE",
                "1 bag garlic;BAG",
                "1 package sausage;PACKAGE",
                "1 pinch salt;PINCH",
                "1 slice cheese;SLICE",
            ],
        )
        fun unit(line: String, unit: IngredientUnit) {
            assertEquals(unit, parse(line).unit)
        }

        // Adapted: "large" is a size word and stays in the name instead of being a unit.
        @Test
        fun `1 large onion`() {
            val line = parse("1 large onion")
            assertNull(line.unit)
            assertEquals("large onion", line.name)
        }

        @Test
        fun `1 whole onion`() {
            assertNull(parse("1 whole onion").unit)
        }

        // Adapted: the parenthesised text is a note instead of being appended to the name.
        @Test
        fun `1 (14,5 oz) can tomatoes`() {
            val line = parse("1 (14.5 oz) can tomatoes")
            assertEquals(Amount(r(1)), line.amount)
            assertEquals(IngredientUnit.CAN, line.unit)
            assertEquals("tomatoes", line.name)
            assertEquals(listOf("14.5 oz"), line.notes)
        }

        // Adapted: the parenthesised text is a note instead of being part of the name.
        @Test
        fun `25 lb beef stew chunks`() {
            val line = parse("25 lb beef stew chunks (or buy a roast and chop into small cubes)")
            assertEquals(Amount(r(25)), line.amount)
            assertEquals(IngredientUnit.POUND, line.unit)
            assertEquals("beef stew chunks", line.name)
            assertEquals(listOf("or buy a roast and chop into small cubes"), line.notes)
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
            delimiter = ';',
            value = ["1 to 2 chicken breasts", "1 - 2 chicken breasts", "1-2 chicken breasts"],
        )
        fun `range without unit`(line: String) {
            val parsed = parse(line)
            assertEquals(Amount(r(1), r(2)), parsed.amount)
            assertNull(parsed.unit)
            assertEquals("chicken breasts", parsed.name)
        }

        // Adapted: the parenthesised text is a note instead of being appended to the name.
        @Test
        fun `1 (16 oz) box pasta`() {
            val line = parse("1 (16 oz) box pasta")
            assertEquals(IngredientUnit.BOX, line.unit)
            assertEquals("pasta", line.name)
            assertEquals(listOf("16 oz"), line.notes)
        }
    }

    @Test
    fun `no unit provided`() {
        val line = parse("1 tortilla")
        assertNull(line.unit)
        assertEquals(Amount(r(1)), line.amount)
        assertEquals("tortilla", line.name)
    }

    // Adapted: a line without quantity is unmeasured and keeps its text.
    @Test
    fun `no unit and no quantity provided`() {
        assertEquals(Unmeasured("powdered sugar"), IngredientParser.parse("powdered sugar"))
    }

    @Nested
    inner class TranslatesTheAbbreviatedUnits {
        // Adapted: unit identifiers instead of singular or plural display words.
        @ParameterizedTest(name = "{0}")
        @CsvSource(
            delimiter = ';',
            value = [
                "1 c water;CUP", "2 c. water;CUP", "2 cups water;CUP",
                "1 gal water;GALLON", "1 gallons water;GALLON",
                "1 oz water;OUNCE", "1 oz. water;OUNCE", "2 ounces water;OUNCE",
                "1 pt water;PINT", "2 pts water;PINT", "1 pt. water;PINT", "2 pints water;PINT",
                "1 lb water;POUND", "1 lb. water;POUND", "2 lbs water;POUND", "2 pounds water;POUND",
                "1 qt water;QUART", "1 qt. water;QUART", "1 qts water;QUART", "1 quarts water;QUART",
                "1 T water;TABLESPOON", "1 T. water;TABLESPOON", "1 tbs water;TABLESPOON",
                "1 tbsp water;TABLESPOON", "1 tbspn water;TABLESPOON", "1 tablespoon water;TABLESPOON",
                "2 tablespoons water;TABLESPOON",
                "1 tsp water;TEASPOON", "1 tspn water;TEASPOON", "1 t water;TEASPOON", "1 t. water;TEASPOON",
                "2 teaspoons water;TEASPOON",
                "1 g water;GRAM", "1 g. water;GRAM", "2 grams water;GRAM",
                "1 kg water;KILOGRAM", "1 kg. water;KILOGRAM", "2 kilograms water;KILOGRAM",
                "1 l water;LITER", "1 l. water;LITER", "2 liters water;LITER",
                "1 mg water;MILLIGRAM", "1 mg. water;MILLIGRAM", "1 milligrams water;MILLIGRAM",
                "1 ml water;MILLILITER", "1 ml. water;MILLILITER", "1 milliliters water;MILLILITER",
                "2 pinches salt;PINCH",
            ],
        )
        fun unit(line: String, unit: IngredientUnit) {
            assertEquals(unit, parse(line).unit)
        }
    }

    @Nested
    inner class TranslatesTheIngredient {
        @Test
        fun water() {
            assertEquals("water", parse("1 teaspoon water").name)
        }

        @Test
        fun milk() {
            assertEquals("milk", parse("1 teaspoon milk").name)
        }
    }
}
