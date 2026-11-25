package io.github.tycho991.kombu.ui.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.tycho991.kombu.ui.theme.KombuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AccountScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show() {
        composeRule.setContent {
            KombuTheme {
                AccountScreen(
                    server = "https://cloud.example.com",
                    loginName = "alex",
                    onBack = { events += "back" },
                    onLogout = { events += "logout" },
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun showsServerAndLoginName() {
        show()

        composeRule.onNodeWithText("https://cloud.example.com").assertIsDisplayed()
        composeRule.onNodeWithText("alex").assertIsDisplayed()
        composeRule.onNodeWithText("Server").assertIsDisplayed()
        composeRule.onNodeWithText("User").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        assertEquals(listOf("back"), events)
    }

    @Test
    @Config(qualifiers = "en")
    fun cancelledLogoutKeepsTheUserSignedIn() {
        show()

        composeRule.onNodeWithText("Sign out").performClick()
        composeRule.onNodeWithText("Sign out?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Sign out?").assertDoesNotExist()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    @Config(qualifiers = "en")
    fun confirmedLogoutSignsOut() {
        show()

        composeRule.onNodeWithText("Sign out").performClick()
        composeRule.onAllNodesWithText("Sign out")[1].performClick()

        assertEquals(listOf("logout"), events)
    }

    @Test
    @Config(qualifiers = "de")
    fun showsGermanTexts() {
        show()

        composeRule.onNodeWithText("Konto").assertIsDisplayed()
        composeRule.onNodeWithText("Benutzer").assertIsDisplayed()
        composeRule.onNodeWithText("Abmelden").performClick()
        composeRule.onNodeWithText("Abmelden?").assertIsDisplayed()
        composeRule.onNodeWithText("Kombu löscht die gespeicherte Anmeldung", substring = true).assertIsDisplayed()
    }
}
