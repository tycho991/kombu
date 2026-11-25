package io.github.tycho991.kombu.data.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** Name of the DataStore file that holds the account; it is excluded from backups (design D9). */
const val ACCOUNT_DATASTORE_NAME = "account"

/**
 * Stores the credentials of the signed-in account. The app password is encrypted with AES-GCM under a key from
 * [keySource]; server address and login name are stored as they are. Data that cannot be read or decrypted is
 * deleted and reported as signed out (design D8).
 */
class CredentialStore(private val dataStore: DataStore<Preferences>, private val keySource: SecretKeySource) {
    /** The stored credentials, or `null` when signed out. */
    val credentials: Flow<NextcloudCredentials?> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { preferences -> decode(preferences) }
            .distinctUntilChanged()

    /** Replaces the stored credentials with [credentials]. */
    suspend fun save(credentials: NextcloudCredentials) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keySource.getOrCreateKey()) }
        val ciphertext = cipher.doFinal(credentials.appPassword.toByteArray(Charsets.UTF_8))
        dataStore.edit { preferences ->
            preferences[SERVER] = credentials.server.toString()
            preferences[LOGIN_NAME] = credentials.loginName
            preferences[PASSWORD_IV] = base64.encodeToString(cipher.iv)
            preferences[PASSWORD_CIPHERTEXT] = base64.encodeToString(ciphertext)
        }
    }

    /** Deletes the stored credentials. */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private suspend fun decode(preferences: Preferences): NextcloudCredentials? {
        if (preferences.asMap().isEmpty()) return null
        val credentials = decodeOrNull(preferences)
        if (credentials == null) clear()
        return credentials
    }

    private fun decodeOrNull(preferences: Preferences): NextcloudCredentials? {
        val server = preferences[SERVER]?.toHttpUrlOrNull()
        val loginName = preferences[LOGIN_NAME]
        val appPassword = decrypt(preferences[PASSWORD_IV], preferences[PASSWORD_CIPHERTEXT])
        return if (server != null && loginName != null && appPassword != null) {
            NextcloudCredentials(ServerAddress.of(server), loginName, appPassword)
        } else {
            null
        }
    }

    private fun decrypt(iv: String?, ciphertext: String?): String? = try {
        if (iv == null || ciphertext == null) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_BITS, decoder.decode(iv))
            cipher.init(Cipher.DECRYPT_MODE, keySource.getOrCreateKey(), spec)
            String(cipher.doFinal(decoder.decode(ciphertext)), Charsets.UTF_8)
        }
    } catch (ignored: GeneralSecurityException) {
        // Wrong or lost key, or tampered data: the spec treats this as signed out.
        null
    } catch (ignored: IllegalArgumentException) {
        // Stored values that are not valid Base64; handled like a decryption failure.
        null
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        val SERVER = stringPreferencesKey("server")
        val LOGIN_NAME = stringPreferencesKey("login_name")
        val PASSWORD_IV = stringPreferencesKey("app_password_iv")
        val PASSWORD_CIPHERTEXT = stringPreferencesKey("app_password_ciphertext")
        val base64: Base64.Encoder = Base64.getEncoder()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}
