package io.github.tycho991.kombu.ui.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tycho991.kombu.data.account.AccountService
import io.github.tycho991.kombu.nextcloudauth.LoginSession
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where the login currently is. */
enum class LoginStep {
    /** The user enters the server address. */
    ENTER_ADDRESS,

    /** The server is being checked and the login is starting. */
    CHECKING_SERVER,

    /** The login page is open in the browser; Kombu waits for the approval. */
    WAITING_FOR_APPROVAL,
}

/** What the login screen shows. */
data class LoginUiState(
    val address: String = "",
    val step: LoginStep = LoginStep.ENTER_ADDRESS,
    val error: NextcloudError? = null,
    val browserMissing: Boolean = false,
)

/**
 * Runs the login (design D5): starts Login Flow v2, asks the UI to open the login page, and waits for the
 * approval. The running session is kept in [savedState], so polling resumes after process death.
 */
class LoginViewModel(private val accounts: AccountService, private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState(address = savedState[KEY_INPUT] ?: ""))
    private val loginPageRequests = Channel<String>(Channel.BUFFERED)
    private val wakeUps = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    /** The state of the login screen. */
    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

    /** Login page URLs the UI must open in a browser, one element per request. */
    val loginPages: Flow<String> = loginPageRequests.receiveAsFlow()

    init {
        restoreSession()?.let { session -> job = viewModelScope.launch { await(session) } }
    }

    /** The user edited the address. */
    fun onAddressChange(value: String) {
        savedState[KEY_INPUT] = value
        mutableState.update { it.copy(address = value, error = null) }
    }

    /** The user asked to sign in with the entered address. */
    fun signIn() {
        if (mutableState.value.step != LoginStep.ENTER_ADDRESS) return
        mutableState.update { it.copy(step = LoginStep.CHECKING_SERVER, error = null, browserMissing = false) }
        job =
            viewModelScope.launch {
                when (val started = accounts.startLogin(mutableState.value.address)) {
                    is NextcloudResult.Failure -> fail(started.error)

                    is NextcloudResult.Success -> {
                        saveSession(started.value)
                        loginPageRequests.send(started.value.loginUrl.toString())
                        await(started.value)
                    }
                }
            }
    }

    /** The user asked to open the login page again. */
    fun reopenLoginPage() {
        savedState.get<String>(KEY_LOGIN_URL)?.let { loginPageRequests.trySend(it) }
    }

    /** The screen became visible again, typically after the browser: poll at once. */
    fun onResume() {
        wakeUps.trySend(Unit)
    }

    /** The UI found no app that can open the login page. */
    fun onBrowserMissing() {
        mutableState.update { it.copy(browserMissing = true) }
    }

    /** The user cancelled the login; nothing is stored. */
    fun cancel() {
        job?.cancel()
        clearSession()
        mutableState.update { LoginUiState(address = it.address) }
    }

    private suspend fun await(session: LoginSession) {
        mutableState.update { it.copy(step = LoginStep.WAITING_FOR_APPROVAL) }
        when (val result = accounts.completeLogin(session, wakeUps)) {
            // The account flow switches to the start screen; the form is reset for a later login.
            is NextcloudResult.Success -> {
                clearSession()
                mutableState.update { LoginUiState(address = it.address) }
            }

            is NextcloudResult.Failure -> fail(result.error)
        }
    }

    private fun fail(error: NextcloudError) {
        clearSession()
        mutableState.update { it.copy(step = LoginStep.ENTER_ADDRESS, error = error) }
    }

    private fun saveSession(session: LoginSession) {
        savedState[KEY_ADDRESS] = session.address.url.toString()
        savedState[KEY_LOGIN_URL] = session.loginUrl.toString()
        savedState[KEY_POLL_ENDPOINT] = session.pollEndpoint.toString()
        savedState[KEY_TOKEN] = session.token
        savedState[KEY_STARTED_AT] = session.startedAtMillis
    }

    private fun clearSession() {
        SESSION_KEYS.forEach { savedState.remove<Any>(it) }
    }

    private fun restoreSession(): LoginSession? {
        val token = savedState.get<String>(KEY_TOKEN)
        val startedAt = savedState.get<Long>(KEY_STARTED_AT)
        val urls =
            listOf(KEY_ADDRESS, KEY_LOGIN_URL, KEY_POLL_ENDPOINT).mapNotNull { key ->
                savedState.get<String>(key)?.toHttpUrlOrNull()
            }
        return if (token == null || startedAt == null || urls.size != SESSION_URL_COUNT) {
            null
        } else {
            LoginSession(ServerAddress.of(urls[0]), urls[1], urls[2], token, startedAt)
        }
    }

    private companion object {
        const val KEY_INPUT = "login.input"
        const val KEY_ADDRESS = "login.address"
        const val KEY_LOGIN_URL = "login.loginUrl"
        const val KEY_POLL_ENDPOINT = "login.pollEndpoint"
        const val KEY_TOKEN = "login.token"
        const val KEY_STARTED_AT = "login.startedAt"
        const val SESSION_URL_COUNT = 3
        val SESSION_KEYS = listOf(KEY_ADDRESS, KEY_LOGIN_URL, KEY_POLL_ENDPOINT, KEY_TOKEN, KEY_STARTED_AT)
    }
}
