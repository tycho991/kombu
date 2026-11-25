// The parse steps follow @magrinj/parse-ingredients 1.0.0 (MIT, Copyright (c) 2021 Jérémy Magrin),
// src/parse.ts; see NOTICE in this module. Kombu changes the behaviour: lines are classified as measured,
// unmeasured or heading, German and English units are recognised together, units may be glued to the
// number, notes come from every parenthesised group and from the first comma, and the result carries a
// unit identifier instead of display text.
package io.github.tycho991.kombu.ingredientparser

/**
 * Parses free-text ingredient lines in German and English, such as `200 g Mehl`, `2 tbsp olive oil`,
 * `2-3EL Orangensaft` or `Salz & Pfeffer`.
 *
 * The parser is stateless and thread-safe. It never throws: every input yields an [IngredientLine] that
 * keeps the original text.
 */
public object IngredientParser {
    private val ARTICLE = Regex("^(?:of|a|an)\\s+", RegexOption.IGNORE_CASE)
    private val WORD = Regex("[^\\s(,]+(?:\\([\\p{L}]{1,3}\\))?")
    private const val HEADING_END = ':'

    /** Parses one ingredient line; see [IngredientLine] for the possible results. */
    public fun parse(line: String): IngredientLine {
        val start = line.indexOfFirst { !it.isWhitespace() }
        val quantity = if (start < 0) null else QuantityReader.read(line, start)
        return when {
            quantity != null -> measured(line, quantity)
            start < 0 -> IngredientLine.Unmeasured(line)
            else -> unmeasured(line, line.trim())
        }
    }

    private fun unmeasured(line: String, text: String): IngredientLine {
        val heading = text.removeSuffix(HEADING_END.toString()).trim()
        return if (text.last() == HEADING_END && heading.isNotEmpty()) {
            IngredientLine.Heading(line, heading)
        } else {
            IngredientLine.Unmeasured(line)
        }
    }

    private fun measured(line: String, quantity: QuantityMatch): IngredientLine.Measured {
        val rest = line.substring(quantity.end).trim()
        val leadingNote = leadingGroup(rest)
        // A unit may follow a leading note, as in `1 (14.5 oz) can tomatoes`; the note stays a note.
        val unitStart = if (leadingNote == null) rest else rest.substring(leadingNote.length).trimStart()
        val unit = findUnit(unitStart)
        val remainder =
            if (unit == null) {
                rest
            } else {
                val name = unitStart.substring(unit.second.length).trimStart().replaceFirst(ARTICLE, "")
                if (leadingNote == null) name else "$leadingNote $name"
            }
        val parts = NoteSplitter.split(remainder)
        return IngredientLine.Measured(
            original = line,
            amount = quantity.amount,
            approximate = quantity.approximate,
            unit = unit?.first,
            unitText = unit?.second,
            name = parts.name,
            notes = parts.notes,
            quantityRange = quantity.range,
        )
    }

    /** Returns the parenthesised group at the start of [text], such as `(14.5 oz)`, or `null`. */
    private fun leadingGroup(text: String): String? {
        if (!text.startsWith('(')) return null
        var depth = 0
        val end =
            text.indices.firstOrNull { index ->
                when (text[index]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                depth == 0
            }
        return end?.let { text.substring(0, it + 1) }
    }

    /**
     * Finds the unit at the start of [text] and returns it with its text as written. Longer phrases are
     * tried first, so a two-word unit such as `measuring cup` wins over a one-word unit.
     */
    private fun findUnit(text: String): Pair<IngredientUnit, String>? {
        val words = mutableListOf<MatchResult>()
        var position = 0
        while (words.size < UnitVocabulary.maxWords) {
            val word = WORD.matchAt(text, position) ?: break
            words += word
            position = word.range.last + 1
            while (position < text.length && text[position].isWhitespace()) position++
        }
        return (words.size downTo 1).firstNotNullOfOrNull { count ->
            val phrase = text.substring(0, words[count - 1].range.last + 1)
            UnitVocabulary.find(phrase)?.let { it to phrase }
        }
    }
}
