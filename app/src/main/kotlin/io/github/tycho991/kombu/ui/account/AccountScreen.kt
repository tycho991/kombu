package io.github.tycho991.kombu.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.tycho991.kombu.R
import io.github.tycho991.kombu.ui.theme.KombuTheme

private val ContentPadding = 24.dp
private val ItemSpacing = 16.dp

/** Shows the signed-in [server] and [loginName] and offers logout after a confirmation. */
@Composable
fun AccountScreen(
    server: String,
    loginName: String,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(ContentPadding),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.account_back)) }
            Text(text = stringResource(R.string.account_title), style = MaterialTheme.typography.headlineMedium)
            LabeledValue(stringResource(R.string.account_server), server)
            LabeledValue(stringResource(R.string.account_login_name), loginName)
            Button(onClick = { confirming = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_sign_out))
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.logout_confirm_title)) },
            text = { Text(stringResource(R.string.logout_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onLogout()
                }) { Text(stringResource(R.string.logout_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.logout_dismiss)) }
            },
        )
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountScreenPreview() {
    KombuTheme {
        AccountScreen(server = "https://cloud.example.com", loginName = "alex", onBack = {}, onLogout = {})
    }
}
