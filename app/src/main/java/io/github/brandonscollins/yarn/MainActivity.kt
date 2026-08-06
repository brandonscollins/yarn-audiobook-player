package io.github.brandonscollins.yarn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.ui.nav.Routes
import io.github.brandonscollins.yarn.ui.nav.YarnApp
import io.github.brandonscollins.yarn.ui.theme.YarnTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PlexGraph.prefs(this)
        val startDestination =
            when {
                prefs.accountToken.isEmpty() -> Routes.LOGIN
                prefs.serverId.isEmpty() -> Routes.SERVERS
                prefs.libraryId.isEmpty() -> Routes.LIBRARIES
                else -> Routes.HOME
            }
        setContent {
            YarnTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    YarnApp(startDestination = startDestination)
                }
            }
        }
    }
}
