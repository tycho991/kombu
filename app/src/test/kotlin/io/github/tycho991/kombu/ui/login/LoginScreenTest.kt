package io.github.tycho991.kombu.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.tycho991.kombu.nextcloudauth.AddressError
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.ui.theme.KombuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renders every state and error of the login screen in English and German. */
@RunWith(RobolectricTestRunner::class)
class LoginScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(LoginUiState())
    private var notice by mutableStateOf(false)
    private val events = mutableListOf<String>()

    private fun show() {
        val actions =
            LoginActions(
                onAddressChange = { events += "address:$it" },
                onSignIn = { events += "signIn" },
                onReopen = { events += "reopen" },
                onCancel = { events += "cancel" },
                onDismissNotice = { events += "dismiss" },
            )
        composeRule.setContent { KombuTheme { LoginScreen(state, notice, actions) } }
    }

    private fun assertErrors(messages: Map<NextcloudError, String>) {
        messages.forEach { (error, text) ->
            state = LoginUiState(address = "cloud.example.com", error = error)
            composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
        }
    }

    /** The empty-address message is also the first sentence of the intro, so it is matched as a whole text. */
    private fun assertEmptyAddressError(text: String) {
        state = LoginUiState(error = NextcloudError.InvalidAddress(AddressError.EMPTY))
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun showsEveryErrorInEnglish() {
        show()
        assertEmptyAddressError("Enter the address of your Nextcloud.")
        assertErrors(
            mapOf(
                NextcloudError.InvalidAddress(AddressError.CLEARTEXT) to "only connects over HTTPS",
                NextcloudError.InvalidAddress(AddressError.INVALID) to "not a valid web address",
                NextcloudError.Unreachable to "cannot be reached",
                NextcloudError.CertificateRejected to "does not trust the certificate",
                NextcloudError.NotNextcloud to "No Nextcloud was found",
                NextcloudError.Maintenance to "maintenance mode",
                NextcloudError.LoginExpired to "timed out",
                NextcloudError.Unauthorized to "login failed",
                NextcloudError.CookbookMissing to "needs the Cookbook app",
                NextcloudError.NotFound to "no longer exists",
                NextcloudError.NameTaken to "already has this name",
                NextcloudError.NameMissing to "needs a name",
                NextcloudError.ServerError(502) to "(HTTP 502)",
            ),
        )
    }

    @Test
    @Config(qualifiers = "de")
    fun showsEveryErrorInGerman() {
        show()
        assertEmptyAddressError("Gib die Adresse deiner Nextcloud ein.")
        assertErrors(
            mapOf(
                NextcloudError.InvalidAddress(AddressError.CLEARTEXT) to "nur über HTTPS",
                NextcloudError.InvalidAddress(AddressError.INVALID) to "keine gültige Webadresse",
                NextcloudError.Unreachable to "nicht erreichbar",
                NextcloudError.CertificateRejected to "vertraut dem Zertifikat",
                NextcloudError.NotNextcloud to "keine Nextcloud gefunden",
                NextcloudError.Maintenance to "Wartungsmodus",
                NextcloudError.LoginExpired to "abgelaufen",
                NextcloudError.Unauthorized to "fehlgeschlagen",
                NextcloudError.CookbookMissing to "braucht die App Cookbook",
                NextcloudError.NotFound to "nicht mehr",
                NextcloudError.NameTaken to "schon diesen Namen",
                NextcloudError.NameMissing to "braucht einen Namen",
                NextcloudError.ServerError(502) to "(HTTP 502)",
            ),
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun entersTheAddressAndSignsIn() {
        show()

        composeRule.onNodeWithText("Enter the address of your Nextcloud. Kombu", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Nextcloud address").performTextInput("cloud.example.com")
        composeRule.onAllNodesWithText("Sign in").assertCountEquals(2)
        composeRule.onAllNodesWithText("Sign in")[1].performClick()

        assertEquals(listOf("address:cloud.example.com", "signIn"), events)
    }

    @Test
    @Config(qualifiers = "en")
    fun showsTheWaitingStateWithItsActions() {
        state = LoginUiState(step = LoginStep.WAITING_FOR_APPROVAL, browserMissing = true)
        show()

        composeRule.onNodeWithText("Kombu continues automatically", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("No app was found", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Open login page again").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(listOf("reopen", "cancel"), events)
    }

    @Test
    @Config(qualifiers = "de")
    fun showsTheProgressStatesInGerman() {
        state = LoginUiState(step = LoginStep.CHECKING_SERVER)
        show()
        composeRule.onNodeWithText("Server wird geprüft", substring = true).assertIsDisplayed()

        state = LoginUiState(step = LoginStep.WAITING_FOR_APPROVAL)
        composeRule.onNodeWithText("Kombu macht danach automatisch weiter", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Anmeldeseite erneut öffnen").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun showsAndDismissesTheOfflineLogoutNotice() {
        notice = true
        show()

        composeRule.onNodeWithText("app password is still listed", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("OK").performClick()

        assertEquals(listOf("dismiss"), events)
    }

    @Test
    @Config(qualifiers = "de")
    fun showsTheOfflineLogoutNoticeInGerman() {
        notice = true
        show()

        composeRule.onNodeWithText("steht sein App-Passwort noch", substring = true).assertIsDisplayed()
    }
}
