package io.github.brandonscollins.yarn.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.Track
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.player.PlayerPrefs
import io.github.brandonscollins.yarn.player.SEEK_STEP_MS
import io.github.brandonscollins.yarn.ui.common.PauseGlyph
import io.github.brandonscollins.yarn.ui.common.formatDuration
import io.github.brandonscollins.yarn.ui.common.formatMmSs
import io.github.brandonscollins.yarn.ui.common.thumbUri

private val SPEED_OPTIONS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

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
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Top half: cover + titles.
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AsyncImage(
                    model = thumbUri(prefs, book?.thumbPath.orEmpty()),
                    contentDescription = null,
                    modifier =
                        Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    book?.title.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    currentTrack?.title.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(formatDuration(durationMs), style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeekButton(label = "-30", onClick = { controller.seekBy(-SEEK_STEP_MS) })
                    IconButton(
                        onClick = { if (isPlaying) controller.pause() else controller.play() },
                        modifier =
                            Modifier.size(80.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        if (isPlaying) {
                            PauseGlyph(modifier = Modifier.size(32.dp))
                        } else {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    SeekButton(label = "+30", onClick = { controller.seekBy(SEEK_STEP_MS) })
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { controller.setSpeed(nextSpeed(speed)) }) {
                        Text("${formatSpeed(speed)}x")
                    }
                    if (sleepRemainingMs != null) {
                        AssistChip(
                            onClick = { controller.cancelSleep() },
                            label = { Text("Sleep ${formatMmSs(sleepRemainingMs!!)}") },
                        )
                    } else {
                        TextButton(
                            onClick = { controller.armSleep(playerPrefs.defaultDurationMin * 60_000L) },
                        ) { Text("Sleep") }
                    }
                    TextButton(onClick = { showChapters = true }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chapters")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showChapters) {
        ModalBottomSheet(onDismissRequest = { showChapters = false }) {
            LazyColumn(modifier = Modifier.fillMaxHeight(0.8f)) {
                itemsIndexed(tracks) { index, track ->
                    ListItem(
                        headlineContent = { Text(track.title) },
                        trailingContent = { Text(formatDuration(track.durationMs)) },
                        colors =
                            if (index == trackIndex) {
                                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            } else {
                                ListItemDefaults.colors()
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
}

@Composable
private fun SeekButton(
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private fun nextSpeed(current: Float): Float {
    val index = SPEED_OPTIONS.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
    val nextIndex = if (index < 0) 0 else (index + 1) % SPEED_OPTIONS.size
    return SPEED_OPTIONS[nextIndex]
}

private fun formatSpeed(speed: Float): String = "%.2f".format(speed).trimEnd('0').trimEnd('.')
