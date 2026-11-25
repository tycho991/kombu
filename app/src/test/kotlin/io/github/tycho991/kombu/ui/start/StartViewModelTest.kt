package io.github.tycho991.kombu.ui.start

import io.github.tycho991.kombu.data.recipes.SyncStatus
import io.github.tycho991.kombu.ui.FakeSyncControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// Dispatchers.setMain and UnconfinedTestDispatcher are the standard way to test viewModelScope.
@OptIn(ExperimentalCoroutinesApi::class)
class StartViewModelTest {
    private val status = MutableStateFlow(SyncStatus())
    private val sync = FakeSyncControl()

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun combinesTheStoredStatusWithTheRunningState() = runTest {
        val viewModel = StartViewModel(status, sync)

        status.value = SyncStatus(recipeCount = 5)
        sync.running.value = true

        assertEquals(StartUiState(SyncStatus(recipeCount = 5), running = true), viewModel.state.first { it.running })
    }

    @Test
    fun syncNowAsksTheScheduler() {
        StartViewModel(status, sync).syncNow()

        assertEquals(listOf("syncNow"), sync.calls)
    }
}
