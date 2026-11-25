package io.github.tycho991.kombu.ui.login

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.tycho991.kombu.R
import io.github.tycho991.kombu.cookbookclient.CookbookVersion
import io.github.tycho991.kombu.nextcloudauth.AddressError
import io.github.tycho991.kombu.nextcloudauth.NextcloudError

/** The user-facing explanation of each error kind (design D7), from the string resources. */
@Composable
fun NextcloudError.message(): String = when (this) {
    is NextcloudError.ServerError -> stringResource(R.string.error_server, statusCode)

    is NextcloudError.CookbookTooOld ->
        stringResource(R.string.error_cookbook_too_old, version, CookbookVersion.MINIMUM.toString())

    else -> stringResource(messageResource())
}

/** The string resource of an error kind whose message has no arguments. */
@StringRes
private fun NextcloudError.messageResource(): Int = when (this) {
    is NextcloudError.InvalidAddress -> reason.messageResource()
    NextcloudError.Unreachable -> R.string.error_unreachable
    NextcloudError.CertificateRejected -> R.string.error_certificate
    NextcloudError.NotNextcloud -> R.string.error_not_nextcloud
    NextcloudError.Maintenance -> R.string.error_maintenance
    NextcloudError.LoginExpired -> R.string.error_login_expired
    NextcloudError.Unauthorized -> R.string.error_unauthorized
    NextcloudError.CookbookMissing -> R.string.error_cookbook_missing
    is NextcloudError.CookbookTooOld -> R.string.error_cookbook_too_old
    NextcloudError.NotFound -> R.string.error_not_found
    NextcloudError.NameTaken -> R.string.error_name_taken
    NextcloudError.NameMissing -> R.string.error_name_missing
    is NextcloudError.ServerError -> R.string.error_server
}

@StringRes
private fun AddressError.messageResource(): Int = when (this) {
    AddressError.EMPTY -> R.string.error_address_empty
    AddressError.CLEARTEXT -> R.string.error_address_cleartext
    AddressError.INVALID -> R.string.error_address_invalid
}
