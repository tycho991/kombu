package io.github.tycho991.kombu.ingredientparser

import io.github.tycho991.kombu.ingredientparser.IngredientLine.Heading
import io.github.tycho991.kombu.ingredientparser.IngredientLine.Measured
import io.github.tycho991.kombu.ingredientparser.IngredientLine.Unmeasured
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource

/** Runs the parser over real ingredient lines (spec ingredient-parsing, "Regression against real lines"). */
class RealLinesFixtureTest {
    @ParameterizedTest(name = "{0}")
    @CsvFileSource(
        resources = [FIXTURES],
        delimiter = '\t',
        useHeadersInDisplayName = true,
        ignoreLeadingAndTrailingWhitespace = false,
    )
    fun `parses real lines as expected`(
        original: String,
        kind: String,
        min: String?,
        max: String?,
        unit: String?,
        name: String?,
        notes: String?,
    ) {
        val expected =
            when (kind) {
                "measured" ->
                    listOf(kind, min, max, unit.orEmpty(), name.orEmpty(), notes.orEmpty())

                "heading" -> listOf(kind, name)

                else -> listOf(kind)
            }
        assertEquals(expected, describe(IngredientParser.parse(original)))
    }

    @Test
    fun `fixture set has at least 100 lines`() {
        val lines =
            requireNotNull(javaClass.getResource(FIXTURES)).readText().lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(lines.size - 1 >= MIN_FIXTURES, "only ${lines.size - 1} fixtures")
    }

    private fun describe(line: IngredientLine): List<String?> = when (line) {
        is Measured ->
            listOf(
                "measured",
                line.amount.min.toString(),
                line.amount.max.toString(),
                line.unit?.name.orEmpty(),
                line.name,
                line.notes.joinToString("|"),
            )

        is Heading -> listOf("heading", line.text)

        is Unmeasured -> listOf("unmeasured")
    }

    private companion object {
        const val FIXTURES = "/fixtures/ingredient-lines.tsv"
        const val MIN_FIXTURES = 100
    }
}
