package io.github.brandonscollins.yarn.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    viewModel: BookDetailViewModel = viewModel(),
) {
    val context = LocalContext.current
    val prefs = remember(context) { PlexGraph.prefs(context) }
    val book by viewModel.book.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val position by viewModel.position.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                                Modifier.size(140.dp).clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Column(
                            modifier = Modifier.padding(start = 16.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(book?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
                            Text(book?.author.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            playerViewModel.controller.playBook(viewModel.book.value?.id ?: return@Button)
                            onOpenPlayer()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (position != null) "Resume" else "Play")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tracks", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(tracks, key = { it.id }) { track ->
                ListItem(
                    leadingContent = {
                        Text(
                            (track.index + 1).toString(),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    headlineContent = { Text(track.title) },
                    trailingContent = {
                        Text(
                            formatDuration(track.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
        }
    }
}
