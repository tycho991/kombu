package io.github.tycho991.kombu.ui

import io.github.tycho991.kombu.sync.SyncControl
import kotlinx.coroutines.flow.MutableStateFlow

/** A [SyncControl] that records the calls and whose running state the test sets. */
class FakeSyncControl : SyncControl {
    override val running = MutableStateFlow(false)
    val calls = mutableListOf<String>()

    override fun start() {
        calls += "start"
    }

    override fun afterLocalChange() {
        calls += "afterLocalChange"
    }

    override fun syncNow() {
        calls += "syncNow"
    }

    override fun cancelAll() {
        calls += "cancelAll"
    }
}
