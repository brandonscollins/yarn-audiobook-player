package io.github.brandonscollins.yarn.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.brandonscollins.yarn.ui.common.AlphabetRail
import io.github.brandonscollins.yarn.ui.common.ThinProgressBar
import io.github.brandonscollins.yarn.ui.common.formatDuration
import io.github.brandonscollins.yarn.ui.common.sortTitle
import io.github.brandonscollins.yarn.ui.common.thumbUri
import kotlinx.coroutines.launch

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
    val viewMode by viewModel.viewMode.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryTopBar(
            viewMode = viewMode,
            sortMode = sortMode,
            searchQuery = searchQuery,
            onViewModeChange = viewModel::setViewMode,
            onSortModeChange = viewModel::setSortMode,
            onSearchQueryChange = viewModel::setSearchQuery,
        )

        if (searchQuery.isNotBlank()) {
            BookListing(searchResults, prefs, viewMode, sortMode, onOpenBook)
            return@Column
        }

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
                BookListing(allBooks, prefs, viewMode, sortMode, onOpenBook)
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
                BookListing(booksInCollection, prefs, viewMode, sortMode, onOpenBook)
            }
        }
    }
}

private fun viewModeLabel(mode: ViewMode) =
    when (mode) {
        ViewMode.Grid -> "Grid"
        ViewMode.List -> "List"
        ViewMode.ListCompact -> "List, no images"
    }

private fun nextViewMode(mode: ViewMode) =
    when (mode) {
        ViewMode.Grid -> ViewMode.List
        ViewMode.List -> ViewMode.ListCompact
        ViewMode.ListCompact -> ViewMode.Grid
    }

private fun sortLabel(mode: SortMode) =
    when (mode) {
        SortMode.Title -> "Title A-Z"
        SortMode.RecentlyAdded -> "Recently added"
        SortMode.RecentlyPublished -> "Recently published"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    viewMode: ViewMode,
    sortMode: SortMode,
    searchQuery: String,
    onViewModeChange: (ViewMode) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchExpanded) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search title or author") },
                singleLine = true,
            )
            IconButton(onClick = {
                searchExpanded = false
                onSearchQueryChange("")
            }) {
                Icon(Icons.Filled.Close, contentDescription = "Close search")
            }
        } else {
            Text(
                "Library",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onViewModeChange(nextViewMode(viewMode)) }) {
                Text(viewModeLabel(viewMode))
            }
            Box {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Text(sortLabel(sortMode))
                }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(sortLabel(mode)) },
                            onClick = {
                                onSortModeChange(mode)
                                sortMenuExpanded = false
                            },
                            leadingIcon =
                                if (mode == sortMode) {
                                    { Icon(Icons.Filled.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                        )
                    }
                }
            }
            IconButton(onClick = { searchExpanded = true }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        }
    }
}

/** Renders [rows] as a grid or a list per [viewMode]; adds the A-Z rail for list + title sort. */
@Composable
private fun BookListing(
    rows: List<BookRow>,
    prefs: PlexPrefs,
    viewMode: ViewMode,
    sortMode: SortMode,
    onOpenBook: (Int) -> Unit,
) {
    if (viewMode == ViewMode.Grid) {
        BookGrid(rows, prefs, onOpenBook)
        return
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(rows, key = { it.book.id }) { row ->
                BookListRow(row, prefs, showThumb = viewMode == ViewMode.List, onClick = { onOpenBook(row.book.id) })
            }
        }
        if (sortMode == SortMode.Title) {
            val letterIndex = remember(rows) { firstIndexByLetter(rows) }
            AlphabetRail(
                availableLetters = letterIndex.keys,
                onLetterSelected = { letter ->
                    nearestIndex(letterIndex, letter)?.let { index ->
                        scope.launch { listState.scrollToItem(index) }
                    }
                },
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

/** First row index for each letter, assuming [rows] is already sorted by [sortTitle]. */
private fun firstIndexByLetter(rows: List<BookRow>): Map<Char, Int> {
    val map = LinkedHashMap<Char, Int>()
    rows.forEachIndexed { index, row ->
        val letter = sortTitle(row.book.title).firstOrNull()?.uppercaseChar() ?: return@forEachIndexed
        map.putIfAbsent(letter, index)
    }
    return map
}

/** The tapped/dragged letter's index, or the nearest available letter's if it has no books. */
private fun nearestIndex(
    letterIndex: Map<Char, Int>,
    letter: Char,
): Int? {
    if (letterIndex.isEmpty()) return null
    letterIndex[letter]?.let { return it }
    return letterIndex.minByOrNull { kotlin.math.abs(it.key - letter) }?.value
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
        contentPadding = PaddingValues(12.dp),
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

/** One row in List/ListCompact mode: optional thumbnail, title, author + duration, progress. */
@Composable
private fun BookListRow(
    row: BookRow,
    prefs: PlexPrefs,
    showThumb: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showThumb) {
            AsyncImage(
                model = thumbUri(prefs, row.book.thumbPath),
                contentDescription = null,
                modifier =
                    Modifier.size(40.dp).clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.book.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${row.book.author} • ${formatDuration(row.book.durationMs)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.progress?.let {
                Spacer(modifier = Modifier.height(4.dp))
                ThinProgressBar(it)
            }
        }
    }
}
