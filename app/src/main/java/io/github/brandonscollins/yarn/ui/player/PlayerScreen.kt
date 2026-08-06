package io.github.brandonscollins.yarn.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.Track
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.player.PlayerPrefs
import io.github.brandonscollins.yarn.player.SEEK_STEP_MS
import io.github.brandonscollins.yarn.ui.common.formatDuration
import io.github.brandonscollins.yarn.ui.common.formatMmSs
import io.github.brandonscollins.yarn.ui.common.thumbUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember(context) { PlexGraph.db(context) }
    val prefs = remember(context) { PlexGraph.prefs(context) }
    val playerPrefs = remember(context) { PlayerPrefs(context) }
    val controller = playerViewModel.controller

    val bookId by controller.bookId.collectAsState()
    val trackIndex by controller.trackIndex.collectAsState()
    val positionMs by controller.positionMs.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val speed by controller.speed.collectAsState()
    val durationMs by controller.durationMs.collectAsState()
    val sleepRemainingMs by controller.sleepRemainingMs.collectAsState()

    val book by produceState<Audiobook?>(initialValue = null, bookId) {
        value = null
        bookId?.let { id -> db.bookDao().getBook(id).collect { value = it } }
    }
    val tracks by produceState<List<Track>>(initialValue = emptyList(), bookId) {
        bookId?.let { id -> db.trackDao().getTracksForBook(id).collect { value = it } }
    }
    val currentTrack = tracks.getOrNull(trackIndex)

    var showChapters by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableStateOf<Float?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Top half: cover + titles.
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AsyncImage(
                    model = thumbUri(prefs, book?.thumbPath.orEmpty()),
                    contentDescription = null,
                    modifier =
                        Modifier.fillMaxWidth(0.82f).aspectRatio(1f)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    book?.title.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    currentTrack?.title.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // Bottom half: everything one-handed (PRD).
            Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp)) {
                val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
                val sliderPosition = (dragPositionMs ?: positionMs.toFloat()).coerceIn(0f, sliderMax)
                Slider(
                    value = sliderPosition,
                    onValueChange = { dragPositionMs = it },
                    onValueChangeFinished = {
                        dragPositionMs?.let { controller.seekTo(it.toLong()) }
                        dragPositionMs = null
                    },
                    valueRange = 0f..sliderMax,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        formatDuration(sliderPosition.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        formatDuration(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeekPill(Icons.Filled.Replay30, "Back 30 seconds") { controller.seekBy(-SEEK_STEP_MS) }
                    Surface(
                        onClick = { if (isPlaying) controller.pause() else controller.play() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(84.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    SeekPill(Icons.Filled.Forward30, "Forward 30 seconds") { controller.seekBy(SEEK_STEP_MS) }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlPill(
                        icon = Icons.Filled.Speed,
                        label = "%.2fx".format(speed),
                        onClick = { showSpeed = true },
                    )
                    ControlPill(
                        icon = Icons.Filled.Bedtime,
                        label = sleepRemainingMs?.let { formatMmSs(it) } ?: "Sleep",
                        active = sleepRemainingMs != null,
                        onClick = {
                            if (sleepRemainingMs != null) {
                                controller.cancelSleep()
                            } else {
                                controller.armSleep(playerPrefs.defaultDurationMin * 60_000L)
                            }
                        },
                    )
                    ControlPill(
                        icon = Icons.AutoMirrored.Filled.List,
                        label = "Chapters",
                        onClick = { showChapters = true },
                    )
                    ControlPill(
                        icon = Icons.Filled.GraphicEq,
                        label = "Sound",
                        onClick = { showEq = true },
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showChapters) {
        ModalBottomSheet(onDismissRequest = { showChapters = false }) {
            LazyColumn(modifier = Modifier.fillMaxHeight(0.8f)) {
                itemsIndexed(tracks) { index, track ->
                    ListItem(
                        headlineContent = { Text(track.title, style = MaterialTheme.typography.titleSmall) },
                        trailingContent = { Text(formatDuration(track.durationMs)) },
                        colors =
                            if (index == trackIndex) {
                                ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    headlineColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    trailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            } else {
                                ListItemDefaults.colors(containerColor = Color.Transparent)
                            },
                        modifier =
                            Modifier.clickable {
                                controller.seekToTrack(index)
                                showChapters = false
                            },
                    )
                }
            }
        }
    }

    if (showSpeed) {
        SpeedSheet(
            speed = speed,
            onSpeedChange = controller::setSpeed,
            onDismiss = { showSpeed = false },
        )
    }

    if (showEq) {
        EqSheet(controller = controller, onDismiss = { showEq = false })
    }
}

/** Pill-shaped ±30s, flanking the play button. */
@Composable
private fun SeekPill(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.width(80.dp).height(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(28.dp))
        }
    }
}

/** One of the four bottom controls. Gold when its feature is armed (only sleep uses that today). */
@Composable
private fun ControlPill(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color =
            if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        contentColor =
            if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
