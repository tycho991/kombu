package io.github.tycho991.kombu.ingredientparser

import io.github.tycho991.kombu.ingredientparser.IngredientLine.Heading
import io.github.tycho991.kombu.ingredientparser.IngredientLine.Measured
import io.github.tycho991.kombu.ingredientparser.IngredientLine.Unmeasured
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random

class IngredientParserTest {
    private fun measured(line: String): Measured = assertInstanceOf(Measured::class.java, IngredientParser.parse(line))

    private fun r(numerator: Long, denominator: Long = 1) = Rational.of(numerator, denominator)

    @Nested
    inner class EveryLineYieldsAResult {
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "\t\n "])
        fun `blank lines are unmeasured`(line: String) {
            assertEquals(Unmeasured(line), IngredientParser.parse(line))
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "Scheiben geschnitten), 2 Sternanis, 1",
                "Knoblauchzehe(n) , 2 - 3",
                "🍋 Zitrone \u0007",
                "((((",
                "2 (((",
                "2 ))) Mehl",
                ":",
                "1/0 Mehl",
                "3-2 Eier",
                "1234567 g Mehl",
            ],
        )
        fun `odd lines keep their text`(line: String) {
            assertEquals(line, IngredientParser.parse(line).original)
        }

        @Test
        fun `random input never throws`() {
            val random = Random(seed = 42)
            val alphabet = "0123456789 .,/-–()½¼~:aEgLlTt"
            repeat(5_000) {
                val line = String(CharArray(random.nextInt(0, 20)) { alphabet[random.nextInt(alphabet.length)] })
                assertEquals(line, IngredientParser.parse(line).original)
            }
        }
    }

    @Nested
    inner class QuantityAtTheStart {
        @Test
        fun `quantity at the start`() {
            val line = measured("400 g Mehl")
            assertEquals(Amount(r(400)), line.amount)
            assertFalse(line.approximate)
        }

        @Test
        fun `approximation word`() {
            val line = measured("ca. 400ml Rotwein")
            assertEquals(Amount(r(400)), line.amount)
            assertEquals(IngredientUnit.MILLILITER, line.unit)
            assertEquals("Rotwein", line.name)
            assertTrue(line.approximate)
            assertEquals(4..6, line.quantityRange)
        }

        @ParameterizedTest
        @ValueSource(strings = ["~2 EL Öl", "etwa 2 EL Öl", "about 2 tbsp oil", "circa 2 EL Öl"])
        fun `other approximation words`(line: String) {
            assertTrue(measured(line).approximate)
        }

        @ParameterizedTest
        @ValueSource(strings = ["Saft von 3 Orangen", "Eier Größe M (2 Stück)", "Calamari 200 g"])
        fun `a number inside the line is no quantity`(line: String) {
            assertEquals(Unmeasured(line), IngredientParser.parse(line))
        }

        @Test
        fun `leading whitespace is allowed and counted in the quantity position`() {
            assertEquals(2..2, measured("  2 Eier").quantityRange)
        }
    }

    @Nested
    inner class QuantityNotations {
        @Test
        fun `decimal comma`() {
            assertEquals(Amount(r(3, 2)), measured("1,5 l Milch").amount)
        }

        @Test
        fun `decimal point`() {
            assertEquals(Amount(r(1, 8)), measured("0.125 teaspoon cayenne pepper").amount)
        }

        @Test
        fun `mixed number`() {
            assertEquals(Amount(r(3, 2)), measured("1 1/2 cups flour").amount)
        }

        @Test
        fun `unicode fractions`() {
            assertEquals(Amount(r(3, 2)), measured("1½ TL Salz").amount)
            assertEquals(Amount(r(1, 2)), measured("½ TL Salz").amount)
        }
    }

    @Nested
    inner class QuantityRanges {
        @Test
        fun `range without spaces`() {
            assertEquals(Amount(r(2), r(3)), measured("2-3 Knoblauchzehen").amount)
        }

        @Test
        fun `range with words`() {
            assertEquals(Amount(r(2), r(3)), measured("2 bis 3 EL Öl").amount)
            assertEquals(Amount(r(1), r(2)), measured("1 to 2 cups water").amount)
        }

        @Test
        fun `range with spaces and en dash`() {
            assertEquals(Amount(r(2), r(3)), measured("2 - 3 Tomaten").amount)
            assertEquals(Amount(r(2), r(3)), measured("2–3 Tomaten").amount)
        }

        @Test
        fun `range with glued unit`() {
            val line = measured("2-3EL Orangensaft")
            assertEquals(Amount(r(2), r(3)), line.amount)
            assertEquals(IngredientUnit.TABLESPOON, line.unit)
            assertEquals("Orangensaft", line.name)
            assertEquals(0..2, line.quantityRange)
        }

        @Test
        fun `a word starting with to is no range`() {
            val line = measured("1 tomato")
            assertEquals(Amount(r(1)), line.amount)
            assertEquals("tomato", line.name)
        }
    }

    @Nested
    inner class Units {
        @Test
        fun `german abbreviation`() {
            val line = measured("2 EL Olivenöl")
            assertEquals(IngredientUnit.TABLESPOON, line.unit)
            assertEquals("EL", line.unitText)
            assertEquals("Olivenöl", line.name)
        }

        @Test
        fun `english code from a recipe card`() {
            val line = measured("2 tbsp Olivenöl")
            assertEquals(IngredientUnit.TABLESPOON, line.unit)
            assertEquals("Olivenöl", line.name)
        }

        @Test
        fun `english word and article`() {
            val line = measured("1 teaspoon of basil")
            assertEquals(IngredientUnit.TEASPOON, line.unit)
            assertEquals("basil", line.name)
        }

        @Test
        fun `umlaut and ascii spelling`() {
            assertEquals(IngredientUnit.TEASPOON, measured("2 Teelöffel Zucker").unit)
            assertEquals(IngredientUnit.TEASPOON, measured("2 Teeloeffel Zucker").unit)
        }

        @Test
        fun `plural marker`() {
            val line = measured("1 Prise(n) Salz")
            assertEquals(IngredientUnit.PINCH, line.unit)
            assertEquals("Prise(n)", line.unitText)
            assertEquals("Salz", line.name)
        }

        @Test
        fun `glued unit`() {
            val line = measured("400g Mehl")
            assertEquals(Amount(r(400)), line.amount)
            assertEquals(IngredientUnit.GRAM, line.unit)
            assertEquals("Mehl", line.name)
        }

        @Test
        fun `abbreviation with period`() {
            val line = measured("1 Pck. Vanillezucker")
            assertEquals(IngredientUnit.PACKET, line.unit)
            assertEquals("Vanillezucker", line.name)
        }

        @Test
        fun `two-word unit`() {
            val line = measured("1 measuring cup rice")
            assertEquals(IngredientUnit.DOSING_CUP, line.unit)
            assertEquals("measuring cup", line.unitText)
            assertEquals("rice", line.name)
        }

        @Test
        fun `german kitchen units`() {
            assertEquals(IngredientUnit.BUNCH, measured("1 Bund Petersilie").unit)
            assertEquals("Petersilie", measured("1 Bund Petersilie").name)
            assertEquals(IngredientUnit.HANDFUL, measured("1 Handvoll Rucola").unit)
            assertEquals("Rucola", measured("1 Handvoll Rucola").name)
        }

        @Test
        fun `unit without name`() {
            val line = measured("1 Prise(n)")
            assertEquals(IngredientUnit.PINCH, line.unit)
            assertEquals("", line.name)
        }
    }

    @Nested
    inner class CountableItems {
        @Test
        fun `counted item`() {
            val line = measured("2 Zwiebeln")
            assertEquals(Amount(r(2)), line.amount)
            assertNull(line.unit)
            assertNull(line.unitText)
            assertEquals("Zwiebeln", line.name)
        }

        @Test
        fun `size word stays in the name`() {
            val line = measured("2 kleine Zwiebeln")
            assertNull(line.unit)
            assertEquals("kleine Zwiebeln", line.name)
        }

        @Test
        fun `an article is only removed after a unit`() {
            assertEquals("a apple", measured("1 a apple").name)
        }

        @Test
        fun `unknown language`() {
            val line = measured("2 cuillères à soupe de sucre")
            assertEquals(Amount(r(2)), line.amount)
            assertNull(line.unit)
        }
    }

    @Nested
    inner class Notes {
        @Test
        fun `plural marker in the name`() {
            val line = measured("1 Ei(er)")
            assertEquals("Ei(er)", line.name)
            assertEquals(emptyList<String>(), line.notes)
        }

        @Test
        fun `parenthesised note`() {
            val line = measured("1 Bund Basilikum (optional)")
            assertEquals("Basilikum", line.name)
            assertEquals(listOf("optional"), line.notes)
        }

        @Test
        fun `comma note`() {
            val line = measured("0.125 teaspoon cayenne pepper, or to taste")
            assertEquals("cayenne pepper", line.name)
            assertEquals(listOf("or to taste"), line.notes)
        }

        @Test
        fun `several notes keep their order`() {
            val line = measured("1 g Chiliflocken (ca. ½ TL), optional")
            assertEquals("Chiliflocken", line.name)
            assertEquals(listOf("ca. ½ TL", "optional"), line.notes)
        }

        @Test
        fun `note before the name`() {
            val line = measured("2-3 EL (rotes) Pesto")
            assertEquals("Pesto", line.name)
            assertEquals(listOf("rotes"), line.notes)
        }

        @Test
        fun `nested parentheses and commas inside them`() {
            val line = measured("2 EL Butter (weich, (sehr) weich), geschmolzen")
            assertEquals("Butter", line.name)
            assertEquals(listOf("weich, (sehr) weich", "geschmolzen"), line.notes)
        }

        @Test
        fun `group after the comma is its own note`() {
            val line = measured("1 Zwiebel, gehackt (fein)")
            assertEquals("Zwiebel", line.name)
            assertEquals(listOf("gehackt", "fein"), line.notes)
        }

        @Test
        fun `unclosed parenthesis stays text`() {
            assertEquals("Mehl (Type 405", measured("200 g Mehl (Type 405").name)
            assertEquals(listOf("gesiebt (fein"), measured("200 g Mehl, gesiebt (fein").notes)
        }

        @Test
        fun `leading note with a unit after it`() {
            val line = measured("2 (je 400 g) Dosen Tomaten, gehackt")
            assertEquals(IngredientUnit.CAN, line.unit)
            assertEquals("Dosen", line.unitText)
            assertEquals("Tomaten", line.name)
            assertEquals(listOf("je 400 g", "gehackt"), line.notes)
        }

        @Test
        fun `leading note without a unit`() {
            val line = measured("2 (große) Zwiebeln")
            assertNull(line.unit)
            assertEquals("Zwiebeln", line.name)
            assertEquals(listOf("große"), line.notes)
        }

        @Test
        fun `unclosed leading parenthesis`() {
            val line = measured("2 (große Zwiebeln")
            assertNull(line.unit)
            assertEquals("(große Zwiebeln", line.name)
        }

        @Test
        fun `empty notes are dropped`() {
            assertEquals(emptyList<String>(), measured("2 c. coriandre ( )").notes)
        }

        @Test
        fun `long parenthesised word is a note`() {
            assertEquals(listOf("frisch"), measured("1 Zitrone(frisch)").notes)
        }
    }

    @Nested
    inner class LinesWithoutQuantity {
        @ParameterizedTest
        @ValueSource(strings = ["Salz & Pfeffer", "Salz", "Parmesan (optional)", "fine salt to taste"])
        fun `seasoning and other lines are unmeasured`(line: String) {
            assertEquals(Unmeasured(line), IngredientParser.parse(line))
        }
    }

    @Nested
    inner class SectionHeadings {
        @Test
        fun heading() {
            assertEquals(Heading("Für die Füllung:", "Für die Füllung"), IngredientParser.parse("Für die Füllung:"))
        }

        @Test
        fun `heading with spaces around`() {
            assertEquals("Teig", (IngredientParser.parse("  Teig :  ") as Heading).text)
        }

        @Test
        fun `a lone colon is no heading`() {
            assertEquals(Unmeasured(" : "), IngredientParser.parse(" : "))
        }

        @Test
        fun `a measured line ending in a colon stays measured`() {
            assertInstanceOf(Measured::class.java, IngredientParser.parse("2 Eier:"))
        }
    }
}
