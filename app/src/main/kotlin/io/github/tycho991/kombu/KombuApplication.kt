package io.github.tycho991.kombu

import android.app.Application
import androidx.work.Configuration
import io.github.tycho991.kombu.sync.SyncEngine
import io.github.tycho991.kombu.sync.SyncEngineProvider

/**
 * Holds the [AppContainer] for the lifetime of the process and gives the sync worker its engine. As a
 * [Configuration.Provider], it lets WorkManager initialise itself on first use.
 */
class KombuApplication :
    Application(),
    SyncEngineProvider,
    Configuration.Provider {
    /** Created on first use, so that tests and the system can start the app cheaply. */
    val container: AppContainer by lazy { AppContainer(this) }

    override val syncEngine: SyncEngine get() = container.syncEngine

    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()
}
