package io.github.tycho991.kombu.data.account

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.ServerAddress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CredentialStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var file: File
    private lateinit var open: TestDataStore
    private val key = SoftwareKeySource()
    private val credentials =
        NextcloudCredentials(
            ServerAddress.of("https://cloud.example.com/nextcloud".toHttpUrl()),
            "alex",
            "secret-app-password",
        )

    @Before
    fun openStore() {
        file = File(folder.root, "account.preferences_pb")
        open = TestDataStore(file)
    }

    @After
    fun closeStore() {
        open.close()
    }

    private fun store(keySource: SecretKeySource = key) = CredentialStore(open.dataStore, keySource)

    @Test
    fun reportsSignedOutWhenEmpty() = runTest {
        assertNull(store().credentials.first())
    }

    @Test
    fun readsSavedCredentials() = runTest {
        store().save(credentials)

        assertEquals(credentials, store().credentials.first())
    }

    @Test
    fun readsCredentialsAfterReopening() = runTest {
        store().save(credentials)
        open.close()
        open = TestDataStore(file)

        assertEquals(credentials, store().credentials.first())
    }

    @Test
    fun neverStoresThePasswordInPlainText() = runTest {
        store().save(credentials)

        val content = file.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("secret-app-password" in content)
        assertTrue("alex" in content)
    }

    @Test
    fun usesAFreshIvForEveryWrite() = runTest {
        store().save(credentials)
        val first = file.readBytes()
        store().save(credentials)

        assertFalse(first.contentEquals(file.readBytes()))
    }

    @Test
    fun treatsDataUnderAnotherKeyAsSignedOutAndDeletesIt() = runTest {
        store().save(credentials)

        assertNull(store(SoftwareKeySource()).credentials.first())
        assertTrue(open.dataStore.data.first().asMap().isEmpty())
    }

    @Test
    fun treatsDamagedValuesAsSignedOut() = runTest {
        store().save(credentials)
        open.dataStore.edit { it[stringPreferencesKey("app_password_iv")] = "not base64 !" }

        assertNull(store().credentials.first())
        assertTrue(open.dataStore.data.first().asMap().isEmpty())
    }

    @Test
    fun treatsIncompleteDataAsSignedOut() = runTest {
        open.dataStore.edit { it[stringPreferencesKey("server")] = "https://cloud.example.com" }

        assertNull(store().credentials.first())
    }

    @Test
    fun treatsADamagedFileAsSignedOut() = runTest {
        open.close()
        file.writeBytes(byteArrayOf(0x7f, 0x00, 0x13, 0x37))
        open = TestDataStore(file)

        assertNull(store().credentials.first())
    }

    @Test
    fun clearsCredentials() = runTest {
        store().save(credentials)
        store().clear()

        assertNull(store().credentials.first())
    }
}
