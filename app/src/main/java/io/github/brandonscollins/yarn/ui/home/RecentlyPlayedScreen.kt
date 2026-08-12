package io.github.brandonscollins.yarn.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.ui.player.PlayerViewModel

/** "View more" off Home's played shelf: every started book, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyPlayedScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val prefs = remember(context) { PlexGraph.prefs(context) }
    val played by homeViewModel.allPlayed.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recently played") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(played, key = { it.book.id }) { row ->
                PlayedBookItem(row, prefs, Modifier.padding(8.dp)) {
                    playerViewModel.controller.playBook(row.book.id)
                    onOpenPlayer()
                }
            }
        }
    }
}
