package io.github.tycho991.kombu.ui.start

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.tycho991.kombu.ui.theme.KombuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renders the first screen per language and checks that its texts come from the matching resources. */
@RunWith(RobolectricTestRunner::class)
class StartScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "en")
    fun showsEnglishTexts() {
        composeRule.setContent { KombuTheme { StartScreen() } }
        composeRule.onNodeWithText("Your recipes").assertIsDisplayed()
        composeRule.onNodeWithText("Kombu is being set up", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun showsGermanTexts() {
        composeRule.setContent { KombuTheme { StartScreen() } }
        composeRule.onNodeWithText("Deine Rezepte").assertIsDisplayed()
        composeRule.onNodeWithText("Kombu wird gerade eingerichtet", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun fallsBackToEnglishForUnsupportedLanguage() {
        composeRule.setContent { KombuTheme { StartScreen() } }
        composeRule.onNodeWithText("Your recipes").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-night")
    fun rendersInDarkTheme() {
        composeRule.setContent { KombuTheme(darkTheme = true) { StartScreen() } }
        composeRule.onNodeWithText("Your recipes").assertIsDisplayed()
    }
}
