package io.github.brandonscollins.yarn.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.ui.common.ThinProgressBar
import io.github.brandonscollins.yarn.ui.common.formatRemaining
import io.github.brandonscollins.yarn.ui.common.thumbUri
import io.github.brandonscollins.yarn.ui.player.PlayerViewModel
import io.github.brandonscollins.yarn.settings.PlexPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    onOpenBook: (Int) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenRecentlyPlayed: () -> Unit,
    onOpenSettings: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val prefs = remember(context) { PlexGraph.prefs(context) }
    val continueListening by homeViewModel.continueListening.collectAsState()
    val upNext by homeViewModel.upNext.collectAsState()
    val recentlyPlayed by homeViewModel.recentlyPlayed.collectAsState()
    val recentlyAdded by homeViewModel.recentlyAdded.collectAsState()
    val finished by homeViewModel.finished.collectAsState()

    val play = { bookId: Int ->
        playerViewModel.controller.playBook(bookId)
        onOpenPlayer()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yarn") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            if (continueListening == null && recentlyAdded.isEmpty()) {
                Text(
                    "Welcome to Yarn. Open Library to start listening.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                return@Column
            }

            continueListening?.let { cl ->
                SectionHeader("Continue listening")
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    modifier = Modifier.fillMaxWidth().clickable { play(cl.book.id) },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = thumbUri(prefs, cl.book.thumbPath),
                            contentDescription = null,
                            modifier =
                                Modifier.size(88.dp).clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                cl.book.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                cl.book.author,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            cl.progress?.let {
                                ThinProgressBar(it, modifier = Modifier.padding(top = 10.dp))
                            }
                            playedLabel(cl)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                        // One tap resumes (PRD) — the gold disc says so.
                        Box(
                            modifier =
                                Modifier.size(52.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // Absent unless the current book sits in a collection with an unstarted book after it,
            // which for a library of standalone titles is always.
            upNext?.let { book ->
                SectionHeader("Up next")
                Spacer(modifier = Modifier.height(8.dp))
                RecentBookItem(book, prefs, onClick = { play(book.id) })
                Spacer(modifier = Modifier.height(28.dp))
            }

            if (recentlyPlayed.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SectionHeader("Recently played")
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenRecentlyPlayed) { Text("View more") }
                }
                LazyRow {
                    items(recentlyPlayed, key = { it.book.id }) { row ->
                        PlayedBookItem(row, prefs, Modifier.width(124.dp).padding(end = 14.dp)) {
                            play(row.book.id)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            if (recentlyAdded.isNotEmpty()) {
                SectionHeader("Recently added")
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow {
                    items(recentlyAdded, key = { it.id }) { book ->
                        RecentBookItem(book, prefs, onClick = { onOpenBook(book.id) })
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            if (finished.isNotEmpty()) {
                SectionHeader("Finished")
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow {
                    items(finished, key = { it.book.id }) { row ->
                        PlayedBookItem(row, prefs, Modifier.width(124.dp).padding(end = 14.dp)) {
                            play(row.book.id)
                        }
                    }
                }
            }
        }
    }
}

/** Small-caps-ish serif rubric over each shelf. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp,
    )
}

/** "4h 20m left" is the number that matters; a done book says so instead. */
private fun playedLabel(row: PlayedBook): String? =
    if (row.finished) "Finished" else row.remainingMs?.let(::formatRemaining)

/** Shared by both played shelves and the "View more" grid — same cover, same time-left line. */
@Composable
internal fun PlayedBookItem(
    row: PlayedBook,
    prefs: PlexPrefs,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            AsyncImage(
                model = thumbUri(prefs, row.book.thumbPath),
                contentDescription = null,
                modifier =
                    Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            row.progress?.let {
                ThinProgressBar(it, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
        Text(
            row.book.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        playedLabel(row)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RecentBookItem(
    book: Audiobook,
    prefs: PlexPrefs,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(124.dp).padding(end = 14.dp).clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = thumbUri(prefs, book.thumbPath),
            contentDescription = null,
            modifier =
                Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Text(
            book.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
