package io.github.brandonscollins.yarn.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.ui.common.formatDuration
import io.github.brandonscollins.yarn.ui.common.thumbUri
import io.github.brandonscollins.yarn.ui.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenAuthor: (String) -> Unit,
    viewModel: BookDetailViewModel = viewModel(),
) {
    val context = LocalContext.current
    val prefs = remember(context) { PlexGraph.prefs(context) }
    val book by viewModel.book.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val position by viewModel.position.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(book?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        val finished = position?.finished == true
                        DropdownMenuItem(
                            text = { Text(if (finished) "Mark as unfinished" else "Mark as finished") },
                            onClick = {
                                viewModel.setFinished(!finished)
                                menuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Mark as unplayed") },
                            onClick = {
                                viewModel.markUnplayed()
                                menuExpanded = false
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row {
                        AsyncImage(
                            model = thumbUri(prefs, book?.thumbPath.orEmpty()),
                            contentDescription = null,
                            modifier =
                                Modifier.size(140.dp).clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                        Column(
                            modifier = Modifier.padding(start = 16.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(book?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                            val author = book?.author.orEmpty()
                            Text(
                                author,
                                style = MaterialTheme.typography.bodyMedium,
                                // Gold marks it as the one tappable word here (ADR-006: gold for
                                // fills and indicators, never small body text on its own).
                                color = MaterialTheme.colorScheme.primary,
                                modifier =
                                    Modifier.padding(top = 4.dp)
                                        .clickable(enabled = author.isNotBlank()) { onOpenAuthor(author) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            playerViewModel.controller.playBook(viewModel.book.value?.id ?: return@Button)
                            onOpenPlayer()
                        },
                        shape = CircleShape,
                        contentPadding = PaddingValues(vertical = 16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (position != null) "Resume" else "Play",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "TRACKS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.5.sp,
                    )
                }
            }
            items(tracks, key = { it.id }) { track ->
                ListItem(
                    leadingContent = {
                        Text(
                            (track.index + 1).toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    headlineContent = { Text(track.title, style = MaterialTheme.typography.bodyLarge) },
                    trailingContent = {
                        Text(
                            formatDuration(track.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}
