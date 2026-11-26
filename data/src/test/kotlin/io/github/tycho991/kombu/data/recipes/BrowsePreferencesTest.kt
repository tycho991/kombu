package io.github.tycho991.kombu.data.recipes

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.tycho991.kombu.data.account.TestDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Task 3.3: the sort order survives a restart and defaults to name. */
class BrowsePreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the sort order defaults to name and survives a new instance`() = runTest {
        val file = folder.root.resolve("browse.preferences_pb")
        val first = TestDataStore(file)
        val preferences = BrowsePreferences(first.dataStore)
        assertEquals(SortOrder.NAME, preferences.sortOrder.first())

        preferences.setSortOrder(SortOrder.LAST_CHANGE)
        assertEquals(SortOrder.LAST_CHANGE, preferences.sortOrder.first())
        first.close()

        val second = TestDataStore(file)
        assertEquals(SortOrder.LAST_CHANGE, BrowsePreferences(second.dataStore).sortOrder.first())
        second.close()
    }

    @Test
    fun `an unknown stored value reads as name`() = runTest {
        val file = folder.root.resolve("browse.preferences_pb")
        val store = TestDataStore(file)
        store.dataStore.edit { it[stringPreferencesKey("sort_order")] = "RANDOM" }

        assertEquals(SortOrder.NAME, BrowsePreferences(store.dataStore).sortOrder.first())
        store.close()
    }
}
