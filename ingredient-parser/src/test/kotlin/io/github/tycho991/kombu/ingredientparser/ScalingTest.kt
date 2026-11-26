package io.github.tycho991.kombu.ingredientparser

import io.github.tycho991.kombu.ingredientparser.IngredientLine.Measured
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.Locale

class ScalingTest {
    private fun parse(line: String) = IngredientParser.parse(line)

    private fun measured(line: String) = parse(line) as Measured

    private fun r(numerator: Long, denominator: Long = 1) = Rational.of(numerator, denominator)

    @Test
    fun `doubling keeps unit and name`() {
        val scaled = parse("1/3 cup milk").scaledBy(r(2)) as Measured
        assertEquals(Amount(r(2, 3)), scaled.amount)
        assertEquals(IngredientUnit.CUP, scaled.unit)
        assertEquals("milk", scaled.name)
    }

    @Test
    fun `four to six servings`() {
        assertEquals(Amount(r(9, 2)), (parse("3 Eier").scaledBy(r(6, 4)) as Measured).amount)
    }

    @Test
    fun `scaling does not change the input`() {
        val line = measured("3 Eier")
        line.scaledBy(r(2))
        assertEquals(Amount(r(3)), line.amount)
    }

    @Test
    fun `unmeasured lines and headings stay the same`() {
        val salt = parse("Salz")
        val heading = parse("Für den Teig:")
        assertSame(salt, salt.scaledBy(r(3)))
        assertSame(heading, heading.scaledBy(r(3)))
    }

    @Test
    fun `a note stays unscaled`() {
        val scaled = parse("2 EL (30 g) Butter").scaledBy(r(2)) as Measured
        assertEquals(Amount(r(4)), scaled.amount)
        assertEquals(listOf("30 g"), scaled.notes)
    }

    @Test
    fun `rejects zero and negative factors for every kind of line`() {
        for (line in listOf("2 Eier", "Salz", "Teig:")) {
            assertThrows<IllegalArgumentException> { parse(line).scaledBy(Rational.ZERO) }
            assertThrows<IllegalArgumentException> { parse(line).scaledBy(r(-2)) }
        }
        assertThrows<IllegalArgumentException> { measured("2 Eier").displayText(Locale.GERMAN, Rational.ZERO) }
    }

    @ParameterizedTest(name = "{0} × {1}/{2} → {3}")
    @CsvSource(
        delimiter = ';',
        value = [
            "2 EL (30 g) Butter;3;2;3 EL (30 g) Butter",
            "400g Mehl;1;2;200g Mehl",
            "ca. 400ml Rotwein;3;2;ca. 600ml Rotwein",
            "2-3EL Orangensaft;2;1;4–6EL Orangensaft",
            "1,5 l Milch;1;3;0,5 l Milch",
            "0,5 l Milch;3;2;0,75 l Milch",
            "1 1/2 cups flour;2;1;3 cups flour",
            "½ TL Salz;1;2;¼ TL Salz",
            "1/2 TL Salz;3;2;¾ TL Salz",
            "1 Zwiebel;3;2;1½ Zwiebel",
            "1 EL Öl;5;4;1¼ EL Öl",
            "1 cup flour;1;3;⅓ cup flour",
            "200 g Mehl;1;3;66,67 g Mehl",
            "  2 Eier, verquirlt;3;1;  6 Eier, verquirlt",
            "1 Zwiebel;2;1;2 Zwiebel",
            "100 g Butter;23;20;115 g Butter",
            "1 g Hefe;23;20;1,15 g Hefe",
        ],
    )
    fun `scaled text replaces only the quantity`(line: String, numerator: Long, denominator: Long, expected: String) {
        assertEquals(expected, measured(line).displayText(Locale.GERMAN, r(numerator, denominator)))
    }

    @Test
    fun `display text without factor shows the current amount`() {
        assertEquals("1.15 g yeast", measured("1,15 g yeast").displayText(Locale.ENGLISH))
        val doubled = measured("2 Eier").scaledBy(r(2)) as Measured
        assertEquals("4 Eier", doubled.displayText(Locale.GERMAN))
    }
}
