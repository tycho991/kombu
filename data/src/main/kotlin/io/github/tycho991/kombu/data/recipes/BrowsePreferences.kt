package io.github.tycho991.kombu.data.recipes

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Name of the DataStore file that holds the browsing preferences. */
const val BROWSE_DATASTORE_NAME = "browse"

/**
 * The recipe list's settings that survive an app restart (design D7 of recipe-browse-edit): only the sort order.
 * Search text and filters are not kept.
 */
class BrowsePreferences(private val dataStore: DataStore<Preferences>) {
    /** The chosen sort order; [SortOrder.NAME] until the user picks another or when the file is unreadable. */
    val sortOrder: Flow<SortOrder> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { preferences ->
                SortOrder.entries.firstOrNull { it.name == preferences[SORT_ORDER] } ?: SortOrder.NAME
            }
            .distinctUntilChanged()

    /** Stores [order] as the sort order. */
    suspend fun setSortOrder(order: SortOrder) {
        dataStore.edit { it[SORT_ORDER] = order.name }
    }

    private companion object {
        val SORT_ORDER = stringPreferencesKey("sort_order")
    }
}
