package io.github.tycho991.kombu.ui.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tycho991.kombu.data.recipes.SyncStatus
import io.github.tycho991.kombu.sync.SyncControl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the start screen shows about the local copy (spec recipe-sync, "Sync status"). */
data class StartUiState(
    /** Counts, last sync and problem from the local copy. */
    val status: SyncStatus = SyncStatus(),
    /** Whether a sync runs right now. */
    val running: Boolean = false,
)

/** Combines the stored sync status with the running state of the scheduler (design D12). */
class StartViewModel(status: Flow<SyncStatus>, private val sync: SyncControl) : ViewModel() {
    /** The state of the start screen. */
    val state: StateFlow<StartUiState> =
        combine(status, sync.running) { current, running -> StartUiState(current, running) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StartUiState())

    /** The user asked to sync now. */
    fun syncNow() {
        sync.syncNow()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
