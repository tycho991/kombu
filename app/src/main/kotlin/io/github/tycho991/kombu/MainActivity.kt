package io.github.tycho991.kombu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.tycho991.kombu.ui.KombuApp
import io.github.tycho991.kombu.ui.theme.KombuTheme

/** The single activity of the app; it hosts the Compose UI. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KombuApplication).container
        setContent {
            KombuTheme {
                KombuApp(container.accounts, container.recipes.status(), container.sync)
            }
        }
    }
}
