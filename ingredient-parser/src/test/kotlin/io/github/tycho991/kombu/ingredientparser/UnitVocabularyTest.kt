package io.github.tycho991.kombu.ingredientparser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class UnitVocabularyTest {
    @Test
    fun `no spelling belongs to two units`() {
        assertEquals(emptyMap<String, Set<IngredientUnit>>(), UnitVocabulary.conflicts())
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
        delimiter = ';',
        value = [
            "Prise;PINCH",
            "Bund;BUNCH",
            "Päckchen;PACKET",
            "Pck.;PACKET",
            "Stück;PIECE",
            "Stängel;STALK",
            "Zweig;SPRIG",
            "Zehe;CLOVE",
            "Scheibe;SLICE",
            "Dose;CAN",
            "Becher;TUB",
            "Spritzer;DASH",
            "Schuss;DASH",
            "Knolle;BULB",
            "Handvoll;HANDFUL",
            "Tütchen;PACKET",
            "Kugel;SCOOP",
            "Blatt;LEAF",
            "Würfel;CUBE",
        ],
    )
    fun `knows the German kitchen units`(word: String, unit: IngredientUnit) {
        assertEquals(unit, UnitVocabulary.find(word))
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
        delimiter = ';',
        value = [
            "Teelöffel;TEASPOON",
            "Teeloeffel;TEASPOON",
            "TEELÖFFEL;TEASPOON",
            "Esslöffel;TABLESPOON",
            "Eßlöffel;TABLESPOON",
            "Essloeffel;TABLESPOON",
            "Stueck;PIECE",
            "Paeckchen;PACKET",
            "Blaetter;LEAF",
            "Wuerfel;CUBE",
        ],
    )
    fun `umlaut spellings match their ASCII forms`(word: String, unit: IngredientUnit) {
        assertEquals(unit, UnitVocabulary.find(word))
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
        delimiter = ';',
        value = [
            "EL;TABLESPOON",
            "El;TABLESPOON",
            "el;TABLESPOON",
            "TL;TEASPOON",
            "Tl;TEASPOON",
            "mL;MILLILITER",
            "ML;MILLILITER",
            "Tbsp;TABLESPOON",
            "T;TABLESPOON",
            "T.;TABLESPOON",
            "t;TEASPOON",
            "t.;TEASPOON",
            "l;LITER",
            "L;LITER",
            "g;GRAM",
        ],
    )
    fun `case is ignored except for single letters`(word: String, unit: IngredientUnit) {
        assertEquals(unit, UnitVocabulary.find(word))
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
        delimiter = ';',
        value = [
            "tsp;TEASPOON",
            "tbsp;TABLESPOON",
            "cups;CUP",
            "c.;CUP",
            "teaspoon;TEASPOON",
            "cloves;CLOVE",
            "handful;HANDFUL",
            "litre;LITER",
            "unit;UNIT",
            "Dosis;DOSE",
            "Pkg.;PACKAGE",
        ],
    )
    fun `knows English and remaining words`(word: String, unit: IngredientUnit) {
        assertEquals(unit, UnitVocabulary.find(word))
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
        delimiter = ';',
        value = [
            "Prise(n);PINCH",
            "Zehe(n);CLOVE",
            "Stück(e);PIECE",
            "Scheibe(n);SLICE",
        ],
    )
    fun `ignores a plural marker`(word: String, unit: IngredientUnit) {
        assertEquals(unit, UnitVocabulary.find(word))
    }

    // Found by the real-line fixtures: "2 Hände Basilikum" means two handfuls.
    @Test
    fun `hand and hands are handfuls`() {
        assertEquals(IngredientUnit.HANDFUL, UnitVocabulary.find("Hand"))
        assertEquals(IngredientUnit.HANDFUL, UnitVocabulary.find("Hände"))
        assertEquals(IngredientUnit.HANDFUL, UnitVocabulary.find("Haende"))
    }

    @Test
    fun `knows units of two words`() {
        assertEquals(IngredientUnit.DOSING_CUP, UnitVocabulary.find("measuring cup"))
        assertEquals(IngredientUnit.HANDFUL, UnitVocabulary.find("Hand  voll"))
        assertEquals(2, UnitVocabulary.maxWords)
    }

    @ParameterizedTest
    @ValueSource(strings = ["klein", "kleine", "groß", "small", "large", "medium", "Zwiebel", "", "G", "C"])
    fun `size words and other words are no units`(word: String) {
        assertNull(UnitVocabulary.find(word))
    }

    @Test
    fun `every unit kind has at least one word`() {
        val covered = (GermanVocabulary.words.keys + EnglishVocabulary.words.keys)
        assertTrue(covered.containsAll(IngredientUnit.entries), "missing: ${IngredientUnit.entries - covered}")
    }
}
