package io.github.tycho991.kombu

import android.app.Application

/** Holds the [AppContainer] for the lifetime of the process. */
class KombuApplication : Application() {
    /** Created on first use, so that tests and the system can start the app cheaply. */
    val container: AppContainer by lazy { AppContainer(this) }
}
