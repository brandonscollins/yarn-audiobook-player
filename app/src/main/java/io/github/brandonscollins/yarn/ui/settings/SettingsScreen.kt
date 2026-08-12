package io.github.brandonscollins.yarn.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.player.PlayerPrefs
import io.github.brandonscollins.yarn.player.REWIND_FIXED
import io.github.brandonscollins.yarn.player.REWIND_OFF
import io.github.brandonscollins.yarn.player.REWIND_SMART
import kotlinx.coroutines.launch

private val REWIND_MODE_LABELS =
    listOf(REWIND_OFF to "Off", REWIND_FIXED to "Fixed", REWIND_SMART to "Smart")

private fun minutesOfDayToText(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun textToMinutesOfDay(text: String): Int? {
    val parts = text.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerPrefs = remember(context) { PlayerPrefs(context) }
    val plexPrefs = remember(context) { PlexGraph.prefs(context) }

    var autoSleepEnabled by remember { mutableStateOf(playerPrefs.autoSleepEnabled) }
    var startText by remember { mutableStateOf(minutesOfDayToText(playerPrefs.windowStartMinutesOfDay)) }
    var endText by remember { mutableStateOf(minutesOfDayToText(playerPrefs.windowEndMinutesOfDay)) }
    var durationText by remember { mutableStateOf(playerPrefs.defaultDurationMin.toString()) }
    var rewindMode by remember { mutableStateOf(playerPrefs.rewindMode) }
    var fixedRewindText by remember { mutableStateOf(playerPrefs.fixedRewindSec.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Auto sleep window", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Any play inside the window arms a fresh timer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Enabled", modifier = Modifier.weight(1f))
                Switch(
                    checked = autoSleepEnabled,
                    onCheckedChange = {
                        autoSleepEnabled = it
                        playerPrefs.autoSleepEnabled = it
                    },
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { text ->
                        startText = text
                        textToMinutesOfDay(text)?.let { playerPrefs.windowStartMinutesOfDay = it }
                    },
                    label = { Text("Start (HH:MM)") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { text ->
                        endText = text
                        textToMinutesOfDay(text)?.let { playerPrefs.windowEndMinutesOfDay = it }
                    },
                    label = { Text("End (HH:MM)") },
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
            }
            OutlinedTextField(
                value = durationText,
                onValueChange = { text ->
                    durationText = text
                    text.toIntOrNull()?.takeIf { it > 0 }?.let { playerPrefs.defaultDurationMin = it }
                },
                label = { Text("Default duration (minutes)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            Text("Rewind on resume", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Smart scales the jump to how long the pause was, up to a minute.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                REWIND_MODE_LABELS.forEach { (mode, label) ->
                    FilterChip(
                        selected = rewindMode == mode,
                        onClick = {
                            rewindMode = mode
                            playerPrefs.rewindMode = mode
                        },
                        label = { Text(label) },
                        shape = CircleShape,
                    )
                }
            }
            if (rewindMode == REWIND_FIXED) {
                OutlinedTextField(
                    value = fixedRewindText,
                    onValueChange = { text ->
                        fixedRewindText = text
                        text.toIntOrNull()?.takeIf { it > 0 }?.let { playerPrefs.fixedRewindSec = it }
                    },
                    label = { Text("Rewind (seconds)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            // Destructive: outlined in the error colour rather than a filled gold pill.
            OutlinedButton(
                onClick = {
                    plexPrefs.accountToken = ""
                    plexPrefs.serverId = ""
                    plexPrefs.serverToken = ""
                    plexPrefs.libraryId = ""
                    plexPrefs.chosenServerUri = ""
                    plexPrefs.serverConnections = emptyList()
                    // Prefs alone left the cached library, the ledger and the live singletons
                    // behind; wipe them before the login screen can start a new session.
                    scope.launch {
                        PlexGraph.reset(context)
                        onSignedOut()
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign out", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
