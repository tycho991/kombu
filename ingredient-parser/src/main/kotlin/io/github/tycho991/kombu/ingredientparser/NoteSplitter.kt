package io.github.tycho991.kombu.ingredientparser

/** The name and notes of the text after quantity and unit. */
internal data class NameAndNotes(val name: String, val notes: List<String>)

/**
 * Splits off notes (design D5, step 5): every top-level group in parentheses, unless it is a plural marker
 * glued to a word such as `Ei(er)`, and the text after the first comma outside parentheses.
 */
internal object NoteSplitter {
    fun split(text: String): NameAndNotes = Scanner(text).run()

    private class Note(val position: Int, val text: String)

    /** Walks the text once, keeping track of the parenthesis depth. */
    private class Scanner(private val text: String) {
        private val notes = mutableListOf<Note>()
        private val name = StringBuilder()
        private val commaNote = StringBuilder()
        private var commaAt = -1
        private var depth = 0
        private var groupStart = -1

        private val target: StringBuilder get() = if (commaAt < 0) name else commaNote

        fun run(): NameAndNotes {
            text.forEachIndexed(::visit)
            // An unclosed parenthesis is kept as plain text.
            if (depth > 0) target.append(text, groupStart, text.length)
            if (commaAt >= 0) notes += Note(commaAt, clean(commaNote))
            return NameAndNotes(
                name = clean(name),
                notes = notes.sortedBy { it.position }.map { it.text }.filter { it.isNotEmpty() },
            )
        }

        private fun visit(index: Int, char: Char) {
            when {
                char == '(' -> open(index)
                char == ')' && depth > 0 -> close(index)
                depth > 0 -> Unit
                char == ',' && commaAt < 0 -> commaAt = index
                else -> target.append(char)
            }
        }

        private fun open(index: Int) {
            if (depth == 0) groupStart = index
            depth++
        }

        private fun close(index: Int) {
            depth--
            if (depth > 0) return
            val group = text.substring(groupStart, index + 1)
            if (isPluralMarker(group)) {
                target.append(group)
            } else {
                notes += Note(groupStart, clean(group.substring(1, group.length - 1)))
            }
        }

        private fun isPluralMarker(group: String): Boolean {
            val inner = group.substring(1, group.length - 1)
            return groupStart > 0 &&
                text[groupStart - 1].isLetter() &&
                inner.length in 1..MAX_PLURAL_MARKER &&
                inner.all { it.isLetter() }
        }
    }

    private const val MAX_PLURAL_MARKER = 3
    private val WHITESPACE = Regex("\\s+")

    private fun clean(text: CharSequence): String = text.trim().replace(WHITESPACE, " ")
}
