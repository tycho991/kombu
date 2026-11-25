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

    private fun show(unsyncedChanges: Int = 0) {
        composeRule.setContent {
            KombuTheme {
                AccountScreen(
                    server = "https://cloud.example.com",
                    loginName = "alex",
                    onBack = { events += "back" },
                    onLogout = { events += "logout" },
                    unsyncedChanges = unsyncedChanges,
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
    fun theConfirmationWithoutPendingChangesWarnsAboutNothingElse() {
        show()

        composeRule.onNodeWithText("Sign out").performClick()

        composeRule.onNodeWithText("deletes the stored login and the recipe copy", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("will be lost", substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en")
    fun theConfirmationNamesThePendingChangesAndCancellingDeletesNothing() {
        show(unsyncedChanges = 3)

        composeRule.onNodeWithText("Sign out").performClick()
        composeRule
            .onNodeWithText("3 changes on this device are not in your Nextcloud yet and will be lost.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(emptyList<String>(), events)
    }

    @Test
    @Config(qualifiers = "de")
    fun theConfirmationNamesOnePendingChangeInGerman() {
        show(unsyncedChanges = 1)

        composeRule.onNodeWithText("Abmelden").performClick()

        composeRule
            .onNodeWithText("1 Änderung auf diesem Gerät ist noch nicht in deiner Nextcloud und geht verloren.")
            .assertIsDisplayed()
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
