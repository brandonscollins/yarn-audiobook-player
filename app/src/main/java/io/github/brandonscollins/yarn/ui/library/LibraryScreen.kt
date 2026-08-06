package io.github.brandonscollins.yarn.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.github.brandonscollins.yarn.data.model.Collection
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.settings.PlexPrefs
import io.github.brandonscollins.yarn.ui.common.ThinProgressBar
import io.github.brandonscollins.yarn.ui.common.thumbUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (Int) -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val prefs = remember(context) { PlexGraph.prefs(context) }
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var selectedCollection by remember { mutableStateOf<Collection?>(null) }
    val allBooks by viewModel.allBooks.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIndex) {
            Tab(
                selected = tabIndex == 0,
                onClick = { tabIndex = 0 },
                text = { Text("All") },
            )
            Tab(
                selected = tabIndex == 1,
                onClick = { tabIndex = 1 },
                text = { Text("Collections") },
            )
        }

        if (tabIndex == 0) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                BookGrid(allBooks, prefs, onOpenBook)
            }
        } else if (selectedCollection == null) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn {
                    items(collections, key = { it.id }) { collection ->
                        ListItem(
                            headlineContent = { Text(collection.title) },
                            modifier = Modifier.clickable { selectedCollection = collection },
                        )
                    }
                }
            }
        } else {
            val collection = selectedCollection!!
            val booksInCollection by remember(collection.id) { viewModel.booksInCollection(collection.id) }
                .collectAsState(initial = emptyList())
            Column(modifier = Modifier.fillMaxSize()) {
                TextButton(onClick = { selectedCollection = null }) { Text("< ${collection.title}") }
                BookGrid(booksInCollection, prefs, onOpenBook)
            }
        }
    }
}

@Composable
private fun BookGrid(
    rows: List<BookRow>,
    prefs: PlexPrefs,
    onOpenBook: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        items(rows, key = { it.book.id }) { row ->
            BookGridItem(row, prefs, onClick = { onOpenBook(row.book.id) })
        }
    }
}

@Composable
private fun BookGridItem(
    row: BookRow,
    prefs: PlexPrefs,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(8.dp).clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            AsyncImage(
                model = thumbUri(prefs, row.book.thumbPath),
                contentDescription = null,
                modifier =
                    Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            row.progress?.let {
                ThinProgressBar(it, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
        Text(
            row.book.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
