package io.github.brandonscollins.yarn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.ui.nav.Routes
import io.github.brandonscollins.yarn.ui.nav.YarnApp
import io.github.brandonscollins.yarn.ui.player.PlayerViewModel
import io.github.brandonscollins.yarn.ui.theme.YarnTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Result ignored: a denied notification permission costs us the notification, not playback. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        val prefs = PlexGraph.prefs(this)
        val startDestination =
            when {
                prefs.accountToken.isEmpty() -> Routes.LOGIN
                prefs.serverId.isEmpty() -> Routes.SERVERS
                prefs.libraryId.isEmpty() -> Routes.LIBRARIES
                else -> Routes.HOME
            }
        // A stored connection URI is only a hint — the winner depends on which network the phone is
        // on right now (gotcha #3), so re-run the race once per launch, before anything needs it.
        if (startDestination == Routes.HOME) {
            lifecycleScope.launch { PlexGraph.connections(this@MainActivity).ensureConnected() }
        }
        handleResumeShortcut(intent)
        setContent {
            YarnTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    YarnApp(startDestination = startDestination)
                }
            }
        }
    }

    /** The activity is singleTop, so a shortcut tapped at a warm app arrives here, not in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleResumeShortcut(intent)
    }

    /**
     * Launcher long-press "Resume": the most recent ledger row through the same `playBook` call
     * Home's Continue-listening card makes. The controller is pulled off this activity's
     * ViewModelStore — the store YarnApp's `viewModel()` resolves against — so there is still one
     * [PlayerViewModel] and one MediaController, and pulling it here rather than inside the
     * coroutine gets the service bind started while Room is read.
     * Nothing ever played means no row, and we just stay on whatever screen we opened on.
     */
    private fun handleResumeShortcut(intent: Intent) {
        if (intent.action != ACTION_RESUME || PlexGraph.prefs(this).libraryId.isEmpty()) return
        val controller = ViewModelProvider(this)[PlayerViewModel::class.java].controller
        lifecycleScope.launch {
            val position = PlexGraph.db(this@MainActivity).positionDao().getMostRecent().first()
            controller.playBook(position?.bookId ?: return@launch)
        }
    }

    /** Media3's playback notification is silently dropped on 33+ without the runtime grant. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        /** Kept in step with the intent action in `res/xml/shortcuts.xml`. */
        const val ACTION_RESUME = "io.github.brandonscollins.yarn.action.RESUME"
    }
}
