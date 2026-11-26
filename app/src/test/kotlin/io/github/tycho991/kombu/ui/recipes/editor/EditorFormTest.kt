package io.github.tycho991.kombu.ui.recipes.editor

import io.github.tycho991.kombu.cookbookclient.RecipeField
import io.github.tycho991.kombu.cookbookclient.RecipeTime
import io.github.tycho991.kombu.cookbookclient.RecipeValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Task 6.2: normalisation and validation of the editor's form (spec "Keywords", "Servings and times"). */
class EditorFormTest {
    private val valid = EditorForm(name = "Soup")

    @Test
    fun `a comma starts the next keyword`() {
        val form = EditorForm().typingKeyword("scharf, indisch")

        assertEquals(listOf("scharf"), form.keywords)
        assertEquals("indisch", form.keywordInput)
        assertEquals(listOf("scharf", "indisch"), form.toValues().values.keywords)
    }

    @Test
    fun `keywords are trimmed and merged ignoring case, keeping the first spelling`() {
        val form = EditorForm(keywords = listOf("vegan")).typingKeyword("Vegan, , quick ,").addingKeyword(" QUICK ")

        assertEquals(listOf("vegan", "quick"), form.keywords)
        assertEquals("", form.keywordInput)
    }

    @Test
    fun `typing without a comma only changes the input`() {
        assertEquals(EditorForm(keywordInput = "veg"), EditorForm().typingKeyword("veg"))
    }

    @Test
    fun `text is trimmed and empty lines and steps are dropped`() {
        val values = valid.copy(
            name = "  Soup ",
            description = "  ",
            category = " Soups ",
            ingredients = "200 g lentils\n\n  1 onion \n",
            steps = listOf(" Chop. ", "", "  "),
        ).toValues().values

        assertEquals(
            RecipeValues(
                name = "Soup",
                category = "Soups",
                ingredients = listOf("200 g lentils", "1 onion"),
                steps = listOf("Chop."),
            ),
            values,
        )
    }

    @Test
    fun `reports each invalid field`() {
        assertEquals(setOf(FormError.NAME_REQUIRED), EditorForm(name = "  ").toValues().errors)
        assertEquals(setOf(FormError.SERVINGS_INVALID), valid.copy(servings = "0").toValues().errors)
        assertEquals(setOf(FormError.SERVINGS_INVALID), valid.copy(servings = "2.5").toValues().errors)
        assertEquals(
            setOf(FormError.PREP_TIME_INVALID),
            valid.copy(prepTime = TimeInput(minutes = "60")).toValues().errors,
        )
        assertEquals(
            setOf(FormError.COOK_TIME_INVALID),
            valid.copy(cookTime = TimeInput(hours = "-1")).toValues().errors,
        )
        assertEquals(
            setOf(FormError.TOTAL_TIME_INVALID),
            valid.copy(totalTime = TimeInput(hours = "x")).toValues().errors,
        )
        assertEquals(emptySet<FormError>(), valid.copy(servings = " 4 ").toValues().errors)
    }

    @Test
    fun `reads hours and minutes, keeps an unreadable time and removes a cleared one`() {
        val values = valid.copy(
            prepTime = TimeInput(minutes = "45"),
            cookTime = TimeInput(hours = "1", minutes = "5"),
            totalTime = TimeInput(raw = "about an hour"),
        ).toValues().values

        assertEquals(RecipeTime.Known(45.minutes), values.prepTime)
        assertEquals(RecipeTime.Known(1.hours + 5.minutes), values.cookTime)
        assertEquals(RecipeTime.Raw("about an hour"), values.totalTime)
        assertNull(valid.copy(cookTime = TimeInput(hours = "0", minutes = "0")).toValues().values.cookTime)
        assertNull(valid.copy(totalTime = TimeInput()).toValues().values.totalTime)
        assertEquals(
            RecipeTime.Known(2.hours),
            valid.copy(totalTime = TimeInput("2", "", raw = "x")).toValues().values.totalTime,
        )
    }

    @Test
    fun `fills the form from stored values`() {
        val form = EditorForm.of(
            RecipeValues(
                name = "Soup",
                servings = 4,
                prepTime = RecipeTime.Known(1.hours + 5.minutes + 30.seconds),
                cookTime = RecipeTime.Known(2.hours),
                totalTime = RecipeTime.Raw("long"),
                ingredients = listOf("a", "b"),
                steps = listOf("Cook."),
            ),
        )

        assertEquals("4", form.servings)
        assertEquals(TimeInput("1", "5"), form.prepTime)
        assertEquals(TimeInput("2", ""), form.cookTime)
        assertEquals(TimeInput(raw = "long"), form.totalTime)
        assertEquals("a\nb", form.ingredients)
        assertTrue(TimeInput("1", "").entered)
        assertFalse(TimeInput(raw = "x").entered)
    }

    @Test
    fun `a new form with nothing entered is blank`() {
        assertTrue(EditorForm().isBlank)
        assertTrue(EditorForm(steps = listOf("")).isBlank)
        assertFalse(EditorForm(description = "x").isBlank)
    }

    @Test
    fun `takes single fields from another form`() {
        val theirs = EditorForm(
            name = "Theirs",
            description = "D",
            category = "C",
            keywords = listOf("k"),
            servings = "2",
        )
        val mine = EditorForm(name = "Mine", keywordInput = "typing")

        assertEquals("Theirs", mine.withField(RecipeField.NAME, theirs).name)
        assertEquals(listOf("k"), mine.withField(RecipeField.KEYWORDS, theirs).keywords)
        assertEquals("", mine.withField(RecipeField.KEYWORDS, theirs).keywordInput)
        val expected = theirs.toValues().values
        RecipeField.entries.forEach { field ->
            assertEquals(field.name, expected[field], mine.withField(field, theirs).toValues().values[field])
        }
    }

    @Test
    fun `moves and replaces list elements`() {
        val steps = listOf("a", "b", "c")

        assertEquals(listOf("c", "a", "b"), steps.moved(2, 0))
        assertEquals(listOf("b", "a", "c"), steps.moved(0, 1))
        assertEquals(steps, steps.moved(0, -1))
        assertEquals(steps, steps.moved(3, 0))
        assertEquals(listOf("a", "x", "c"), steps.replacedAt(1, "x"))
    }
}
