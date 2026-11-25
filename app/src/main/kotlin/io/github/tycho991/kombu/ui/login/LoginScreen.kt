package io.github.tycho991.kombu.ui.login

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tycho991.kombu.R
import io.github.tycho991.kombu.ui.theme.KombuTheme

private val ContentPadding = 24.dp
private val ItemSpacing = 16.dp

/** Connects [LoginScreen] to its [LoginViewModel] and opens the login page in a browser (design D6). */
@Composable
fun LoginRoute(viewModel: LoginViewModel, offlineLogoutNotice: Boolean, onDismissNotice: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.loginPages.collect { url -> if (!openLoginPage(context, url)) viewModel.onBrowserMissing() }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    LoginScreen(
        state = state,
        offlineLogoutNotice = offlineLogoutNotice,
        actions =
        LoginActions(
            onAddressChange = viewModel::onAddressChange,
            onSignIn = viewModel::signIn,
            onReopen = viewModel::reopenLoginPage,
            onCancel = viewModel::cancel,
            onDismissNotice = onDismissNotice,
        ),
    )
}

/** Opens [url] in a Custom Tab, or in any browser without Custom Tabs; returns false when no app can open it. */
fun openLoginPage(context: Context, url: String): Boolean = try {
    CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
    true
} catch (ignored: ActivityNotFoundException) {
    // No browser installed or enabled; the screen explains that instead.
    false
}

/** The callbacks of [LoginScreen]. */
data class LoginActions(
    val onAddressChange: (String) -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onReopen: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onDismissNotice: () -> Unit = {},
)

/** The login screen; all texts come from string resources. */
@Composable
fun LoginScreen(
    state: LoginUiState,
    offlineLogoutNotice: Boolean,
    actions: LoginActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier =
            Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(ContentPadding),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            Text(text = stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
            if (offlineLogoutNotice) OfflineLogoutNotice(actions.onDismissNotice)
            when (state.step) {
                LoginStep.ENTER_ADDRESS -> AddressForm(state, actions)
                LoginStep.CHECKING_SERVER -> Progress(stringResource(R.string.login_checking))
                LoginStep.WAITING_FOR_APPROVAL -> Waiting(state, actions)
            }
        }
    }
}

@Composable
private fun AddressForm(state: LoginUiState, actions: LoginActions) {
    Text(text = stringResource(R.string.login_intro), style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(
        value = state.address,
        onValueChange = actions.onAddressChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.login_address_label)) },
        isError = state.error != null,
        supportingText = state.error?.let { error -> { Text(error.message()) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { actions.onSignIn() }),
    )
    Button(onClick = actions.onSignIn, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.login_sign_in))
    }
}

@Composable
private fun Waiting(state: LoginUiState, actions: LoginActions) {
    Progress(stringResource(R.string.login_waiting))
    if (state.browserMissing) {
        Text(
            text = stringResource(R.string.login_no_browser),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    OutlinedButton(onClick = actions.onReopen, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.login_reopen))
    }
    TextButton(onClick = actions.onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.login_cancel))
    }
}

@Composable
private fun Progress(text: String) {
    CircularProgressIndicator()
    Text(text = text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun OfflineLogoutNotice(onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ItemSpacing)) {
            Text(text = stringResource(R.string.login_signed_out_offline), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.login_notice_dismiss)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    KombuTheme {
        LoginScreen(state = LoginUiState(), offlineLogoutNotice = false, actions = LoginActions())
    }
}
