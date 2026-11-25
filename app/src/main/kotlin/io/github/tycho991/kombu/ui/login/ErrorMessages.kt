package io.github.tycho991.kombu.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.tycho991.kombu.R
import io.github.tycho991.kombu.nextcloudauth.AddressError
import io.github.tycho991.kombu.nextcloudauth.NextcloudError

/** The user-facing explanation of each error kind (design D7), from the string resources. */
@Composable
fun NextcloudError.message(): String = when (this) {
    is NextcloudError.InvalidAddress ->
        when (reason) {
            AddressError.EMPTY -> stringResource(R.string.error_address_empty)
            AddressError.CLEARTEXT -> stringResource(R.string.error_address_cleartext)
            AddressError.INVALID -> stringResource(R.string.error_address_invalid)
        }

    NextcloudError.Unreachable -> stringResource(R.string.error_unreachable)

    NextcloudError.CertificateRejected -> stringResource(R.string.error_certificate)

    NextcloudError.NotNextcloud -> stringResource(R.string.error_not_nextcloud)

    NextcloudError.Maintenance -> stringResource(R.string.error_maintenance)

    NextcloudError.LoginExpired -> stringResource(R.string.error_login_expired)

    NextcloudError.Unauthorized -> stringResource(R.string.error_unauthorized)

    NextcloudError.CookbookMissing -> stringResource(R.string.error_cookbook_missing)

    is NextcloudError.ServerError -> stringResource(R.string.error_server, statusCode)
}
