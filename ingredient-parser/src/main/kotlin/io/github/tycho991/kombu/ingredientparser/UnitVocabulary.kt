// Recognition words partly ported from @magrinj/parse-ingredients 1.0.0 (MIT, Copyright (c) 2021
// Jérémy Magrin), src/locale/de.ts and src/locale/en.ts; see NOTICE in this module. Kombu writes the
// German words with real umlauts, adds kitchen units found in real recipes, drops size words and the
// display translations, and resolves words that upstream maps to two units.
package io.github.tycho991.kombu.ingredientparser

import io.github.tycho991.kombu.ingredientparser.IngredientUnit.BAG
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.BOX
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.BULB
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.BUNCH
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.CAN
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.CENTILITER
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.CLOVE
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.COFFEESPOON
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.CUBE
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.CUP
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.DASH
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.DOSE
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.DOSING_CUP
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.DROP
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.GALLON
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.GRAM
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.HANDFUL
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.KILOGRAM
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.LEAF
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.LITER
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.MILLIGRAM
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.MILLILITER
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.OUNCE
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.PACKAGE
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.PACKET
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.PIECE
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.PINCH
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.PINT
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.POUND
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.QUART
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.SCOOP
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.SLICE
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.SPRIG
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.STALK
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.STICK
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.TABLESPOON
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.TEASPOON
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.TUB
import io.github.tycho991.kombu.ingredientparser.IngredientUnit.UNIT
import java.text.Normalizer
import java.util.Locale

/** German recognition words per unit. Umlaut spellings also match their ASCII forms (design D3). */
internal object GermanVocabulary {
    val words: Map<IngredientUnit, List<String>> =
        mapOf(
            CUP to listOf("Tasse", "Tassen", "Tas."),
            GALLON to listOf("Gallone", "Gallonen"),
            OUNCE to listOf("Unze", "Unzen"),
            PINT to listOf("Pint", "Pints"),
            POUND to listOf("Pfund", "Pfd"),
            QUART to listOf("Quart"),
            TABLESPOON to listOf("Esslöffel", "EL"),
            COFFEESPOON to listOf("Kaffeelöffel", "KL"),
            TEASPOON to listOf("Teelöffel", "TL"),
            GRAM to listOf("Gramm", "gr", "g"),
            KILOGRAM to listOf("Kilogramm", "Kilo", "kg"),
            LITER to listOf("Liter", "lt", "l", "L"),
            CENTILITER to listOf("Zentiliter", "cl"),
            MILLIGRAM to listOf("Milligramm", "mg"),
            MILLILITER to listOf("Milliliter", "ml"),
            PACKAGE to listOf("Packung", "Packungen", "Pkg"),
            PACKET to listOf("Päckchen", "Päckch", "Pck", "Tütchen"),
            STICK to listOf("Stange", "Stangen"),
            PIECE to listOf("Stück", "Stücke", "Stk"),
            PINCH to listOf("Prise", "Prisen"),
            CLOVE to listOf("Zehe", "Zehen"),
            SLICE to listOf("Scheibe", "Scheiben"),
            CAN to listOf("Dose", "Dosen"),
            BOX to listOf("Schachtel", "Schachteln"),
            BAG to listOf("Beutel", "Tüte", "Tüten"),
            LEAF to listOf("Blatt", "Blätter"),
            SPRIG to listOf("Zweig", "Zweige"),
            DOSE to listOf("Dosis"),
            DROP to listOf("Tropfen"),
            CUBE to listOf("Würfel"),
            DOSING_CUP to listOf("Messbecher"),
            BUNCH to listOf("Bund"),
            STALK to listOf("Stängel", "Stiel", "Stiele"),
            DASH to listOf("Spritzer", "Schuss"),
            BULB to listOf("Knolle", "Knollen"),
            HANDFUL to listOf("Handvoll", "Hand voll", "Hand", "Hände"),
            TUB to listOf("Becher"),
            SCOOP to listOf("Kugel", "Kugeln"),
        )
}

/** English recognition words per unit, including the codes used by shared recipe cards (`tsp`, `tbsp`). */
internal object EnglishVocabulary {
    val words: Map<IngredientUnit, List<String>> =
        mapOf(
            CUP to listOf("c", "cup", "cups"),
            GALLON to listOf("gal", "gallon", "gallons"),
            OUNCE to listOf("oz", "ounce", "ounces"),
            PINT to listOf("pt", "pts", "pint", "pints"),
            POUND to listOf("lb", "lbs", "pound", "pounds"),
            QUART to listOf("qt", "qts", "quart", "quarts"),
            TABLESPOON to listOf("tbs", "tbsp", "tbspn", "T", "tablespoon", "tablespoons"),
            COFFEESPOON to listOf("coffeespoon", "coffeespoons"),
            TEASPOON to listOf("tsp", "tspn", "t", "teaspoon", "teaspoons"),
            GRAM to listOf("g", "gram", "grams"),
            KILOGRAM to listOf("kg", "kilogram", "kilograms"),
            LITER to listOf("l", "liter", "liters", "litre", "litres"),
            CENTILITER to listOf("cl", "centiliter", "centiliters"),
            MILLIGRAM to listOf("mg", "milligram", "milligrams"),
            MILLILITER to listOf("ml", "milliliter", "milliliters", "millilitre", "millilitres"),
            PACKAGE to listOf("pkg", "pkgs", "package", "packages"),
            PACKET to listOf("packet", "packets"),
            STICK to listOf("stick", "sticks"),
            PIECE to listOf("pcs", "piece", "pieces"),
            PINCH to listOf("pinch", "pinches"),
            CLOVE to listOf("clove", "cloves"),
            SLICE to listOf("slice", "slices"),
            CAN to listOf("can", "cans"),
            BOX to listOf("box", "boxes"),
            BAG to listOf("bag", "bags"),
            LEAF to listOf("leaf", "leaves"),
            SPRIG to listOf("sprig", "sprigs"),
            UNIT to listOf("unit", "units"),
            DROP to listOf("drop", "drops"),
            CUBE to listOf("cube", "cubes"),
            DOSING_CUP to
                listOf(
                    "measuring cup",
                    "measuring cups",
                    "dosing cup",
                    "dosing cups",
                    "dosing beaker",
                    "dosing beakers",
                ),
            BUNCH to listOf("bunch", "bunches"),
            DASH to listOf("dash", "dashes"),
            HANDFUL to listOf("handful", "handfuls"),
        )
}

/** All recognition words of all languages, merged into one lookup by normalised key (design D3, D4). */
internal object UnitVocabulary {
    private val PLURAL_MARKER = Regex("\\([\\p{L}]{1,3}\\)$")
    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val WHITESPACE = Regex("\\s+")
    private val languages = listOf(GermanVocabulary.words, EnglishVocabulary.words)

    private val candidates: Map<String, Set<IngredientUnit>> =
        buildMap<String, MutableSet<IngredientUnit>> {
            for (language in languages) {
                for ((unit, words) in language) {
                    words.forEach { getOrPut(normalize(it)) { mutableSetOf() }.add(unit) }
                }
            }
        }

    private val lookup: Map<String, IngredientUnit> = candidates.mapValues { it.value.first() }

    /** The largest number of words in one recognition entry, such as two for `measuring cup`. */
    val maxWords: Int = languages.flatMap { it.values.flatten() }.maxOf { it.split(' ').size }

    /** Returns the unit that [phrase] names, or `null` if it is not a known unit. */
    fun find(phrase: String): IngredientUnit? = lookup[normalize(phrase)]

    /** Normalised keys that more than one unit claims; must stay empty, checked by a test. */
    fun conflicts(): Map<String, Set<IngredientUnit>> = candidates.filterValues { it.size > 1 }

    /**
     * Normalises a unit spelling for matching: drops a trailing period and a plural marker such as `(n)`;
     * single letters stay exact (`T` is not `t`); everything else is lower-cased, umlauts become `ae`,
     * `oe`, `ue`, `ß` becomes `ss`, other accents are removed and inner whitespace is collapsed.
     */
    fun normalize(spelling: String): String {
        val bare = spelling.trim().removeSuffix(".").replace(PLURAL_MARKER, "")
        if (bare.length == 1) return bare
        val folded =
            bare
                .lowercase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
        return Normalizer
            .normalize(folded, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .replace(WHITESPACE, " ")
    }
}
