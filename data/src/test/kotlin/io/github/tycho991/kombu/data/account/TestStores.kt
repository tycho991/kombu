package io.github.tycho991.kombu.data.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** A software AES key in place of the Android Keystore, which the JVM tests cannot use. */
class SoftwareKeySource(private val key: SecretKey = newKey()) : SecretKeySource {
    override fun getOrCreateKey(): SecretKey = key

    companion object {
        fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
}

/** A DataStore on [file]; call [close] before opening the same file again. */
class TestDataStore(val file: File) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    fun close() = scope.cancel()
}
