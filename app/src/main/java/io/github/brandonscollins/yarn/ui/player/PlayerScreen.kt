package io.github.brandonscollins.yarn.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.brandonscollins.yarn.data.download.DownloadState
import io.github.brandonscollins.yarn.data.download.Downloads
import io.github.brandonscollins.yarn.data.local.nextUpNext
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.Chapter
import io.github.brandonscollins.yarn.data.model.Track
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.player.SEEK_STEP_MS
import io.github.brandonscollins.yarn.player.absolutePositionMs
import io.github.brandonscollins.yarn.player.bookChapters
import io.github.brandonscollins.yarn.player.nextChapterStart
import io.github.brandonscollins.yarn.player.previousChapterStart
import io.github.brandonscollins.yarn.player.resumePointAt
import io.github.brandonscollins.yarn.ui.common.DownloadMenuItem
import io.github.brandonscollins.yarn.ui.common.DownloadProgress
import io.github.brandonscollins.yarn.ui.common.formatDuration
import io.github.brandonscollins.yarn.ui.common.formatMmSs
import io.github.brandonscollins.yarn.ui.common.thumbUri
import kotlin.math.abs
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember(context) { PlexGraph.db(context) }
    val prefs = remember(context) { PlexGraph.prefs(context) }
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

    // Embedded chapters from Plex, cached in Room by playBook's lazy syncChapters. Empty for a
    // book whose files carry none — everything below then falls back to track boundaries.
    val chapterRows by produceState<List<Chapter>>(initialValue = emptyList(), bookId) {
        value = emptyList()
        bookId?.let { id -> db.chapterDao().getChaptersForBook(id).collect { value = it } }
    }
    val chapters = remember(tracks, chapterRows) { bookChapters(tracks, chapterRows) }

    // Mirrors HomeViewModel.upNext: the next unstarted book of this one's series.
    val upNext by produceState<Audiobook?>(initialValue = null, bookId) {
        value = null
        val id = bookId ?: return@produceState
        val current = db.bookDao().getBook(id).first() ?: return@produceState
        combine(
            db.collectionDao().getCollectionPeers(id),
            db.positionDao().getAll(),
            db.bookDao().getAllBooks(),
        ) { peers, positions, books ->
            val started = positions.mapTo(mutableSetOf()) { it.bookId }
            val peerIds = peers.mapTo(mutableSetOf()) { it.bookId }
            nextUpNext(
                current = current,
                collectionPeers = books.filter { it.id in peerIds },
                sameAuthorBooks = books.filter { it.author == current.author },
                crossRefs = peers,
                startedBookIds = started,
            )?.let { nextId -> books.firstOrNull { it.id == nextId } }
        }.collect { value = it }
    }

    val downloadState by produceState(DownloadState(), bookId) {
        value = DownloadState()
        bookId?.let { id -> Downloads.observe(context, id).collect { value = it } }
    }

    var showChapters by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableStateOf<Float?>(null) }

    // "Downloaded" is only worth saying to someone who watched it happen, so it fires on the
    // running → finished edge, never on opening the Player at an already-downloaded book.
    val snackbarHostState = remember { SnackbarHostState() }
    var wasDownloading by remember(bookId) { mutableStateOf(false) }
    LaunchedEffect(downloadState) {
        if (wasDownloading && !downloadState.downloading && downloadState.downloaded) {
            snackbarHostState.showSnackbar("Downloaded")
        }
        wasDownloading = downloadState.downloading
    }

    // The scrub bar is book-level. Ticks come from Plex's embedded chapters when the server has
    // them (single-file books included), else from track boundaries — a multi-file book's files
    // are its chapters. A single-file book with no embedded chapters gets a plain bar; ID3
    // CHAP / MP4 `chpl` parsing is the remaining Milestone 5 work for those.
    val bookDurationMs = tracks.sumOf { it.durationMs }.takeIf { it > 0 } ?: durationMs
    val chapterFractions =
        remember(tracks, chapters, bookDurationMs) {
            when {
                bookDurationMs <= 0 -> emptyList()
                chapters.isNotEmpty() ->
                    chapters.map { it.startMs.toFloat() / bookDurationMs }.filter { it > 0f && it < 1f }
                tracks.size < 2 -> emptyList()
                else ->
                    tracks.dropLast(1)
                        .runningFold(0L) { acc, track -> acc + track.durationMs }
                        .drop(1)
                        .map { it.toFloat() / bookDurationMs }
            }
        }

    // Absolute starts the chapter-skip buttons step between — embedded chapters where the book has
    // them, else one per track, since a multi-file book's files are its chapters. Neither: no
    // buttons, rather than two dead ones.
    val chapterStarts =
        remember(tracks, chapters) {
            when {
                chapters.isNotEmpty() -> chapters.map { it.startMs }
                tracks.size < 2 -> emptyList()
                else ->
                    tracks.runningFold(0L) { acc, track -> acc + track.durationMs }.dropLast(1)
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    DownloadProgress(downloadState)
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        val id = bookId
                        DownloadMenuItem(
                            state = downloadState,
                            onDownload = {
                                id?.let { Downloads.start(context, it) }
                                menuExpanded = false
                            },
                            onCancel = {
                                id?.let { Downloads.cancel(context, it) }
                                menuExpanded = false
                            },
                            onRemove = {
                                // A remove torn off by navigation self-heals: PlayerController
                                // clears any localUri that no longer opens and streams instead.
                                id?.let { scope.launch { Downloads.remove(context, it) } }
                                menuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Book details") },
                            onClick = {
                                menuExpanded = false
                                id?.let(onOpenDetail)
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                val sliderMax = bookDurationMs.coerceAtLeast(1L).toFloat()
                val absoluteMs = absolutePositionMs(tracks, trackIndex, positionMs)
                val sliderPosition = (dragPositionMs ?: absoluteMs.toFloat()).coerceIn(0f, sliderMax)
                ChapterSlider(
                    positionMs = sliderPosition,
                    durationMs = sliderMax,
                    chapterFractions = chapterFractions,
                    onValueChange = { dragPositionMs = it },
                    onValueChangeFinished = {
                        dragPositionMs?.let {
                            val target = resumePointAt(tracks, it.toLong())
                            controller.seekToTrack(target.trackIndex, target.positionMs)
                        }
                        dragPositionMs = null
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatDuration(sliderPosition.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // The chapter steps sit under the ticks they move between: the transport row
                    // below is already 244dp of fixed-width controls, with nothing left for two
                    // more buttons on a 360dp screen.
                    if (chapterStarts.isNotEmpty()) {
                        val seekTo = { target: Long ->
                            val point = resumePointAt(tracks, target)
                            controller.seekToTrack(point.trackIndex, point.positionMs)
                        }
                        ChapterSkip(
                            icon = Icons.Filled.SkipPrevious,
                            description = "Previous chapter",
                            targetMs = previousChapterStart(chapterStarts, absoluteMs),
                            onSeek = seekTo,
                        )
                        ChapterSkip(
                            icon = Icons.Filled.SkipNext,
                            description = "Next chapter",
                            targetMs = nextChapterStart(chapterStarts, absoluteMs),
                            onSeek = seekTo,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Text(
                        formatDuration(bookDurationMs),
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            if (sleepRemainingMs != null) controller.cancelSleep() else showSleep = true
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

                upNext?.let { next ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { controller.playBook(next.id) }
                                .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Up next",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                next.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showChapters) {
        ModalBottomSheet(onDismissRequest = { showChapters = false }) {
            LazyColumn(modifier = Modifier.fillMaxHeight(0.8f)) {
                if (chapters.isNotEmpty()) {
                    // Current chapter = the last one starting at or before the playhead.
                    val absoluteNowMs = absolutePositionMs(tracks, trackIndex, positionMs)
                    val currentChapter = chapters.indexOfLast { it.startMs <= absoluteNowMs }
                    itemsIndexed(chapters) { index, chapter ->
                        ChapterRow(
                            title = chapter.title,
                            trailing = formatDuration(chapter.startMs),
                            current = index == currentChapter,
                        ) {
                            val target = resumePointAt(tracks, chapter.startMs)
                            controller.seekToTrack(target.trackIndex, target.positionMs)
                            showChapters = false
                        }
                    }
                } else {
                    itemsIndexed(tracks) { index, track ->
                        ChapterRow(
                            title = track.title,
                            trailing = formatDuration(track.durationMs),
                            current = index == trackIndex,
                        ) {
                            controller.seekToTrack(index)
                            showChapters = false
                        }
                    }
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

    if (showSleep) {
        SleepSheet(
            onArm = controller::armSleep,
            onDismiss = { showSleep = false },
        )
    }

    if (showEq) {
        EqSheet(controller = controller, onDismiss = { showEq = false })
    }
}

/**
 * One row of the chapters sheet — an embedded chapter or, in the fallback, a track. [trailing] is
 * the chapter's start time in the book (or the track's duration), and the current row is gold.
 */
@Composable
private fun ChapterRow(
    title: String,
    trailing: String,
    current: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        trailingContent = { Text(trailing) },
        colors =
            if (current) {
                ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    headlineColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                ListItemDefaults.colors(containerColor = Color.Transparent)
            },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * The scrub bar, with one tick per chapter boundary. Only local drag state moves during a gesture —
 * the seek is committed once on release — so a drag never turns into a stream of session IPC.
 */
@Composable
private fun ChapterSlider(
    positionMs: Float,
    durationMs: Float,
    chapterFractions: List<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    // Ink on both halves of the track: gold-on-gold would vanish on the played side.
    val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = positionMs,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..durationMs,
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            // The track is inset by the thumb's radius at each end; that inset is the whole
            // geometry, since a tick has to line up with where the thumb stops.
            val inset = 10.dp.toPx()
            val trackWidth = size.width - inset * 2
            val thumbX = inset + trackWidth * (positionMs / durationMs).coerceIn(0f, 1f)
            val tickHeight = 4.dp.toPx()
            chapterFractions.forEach { fraction ->
                val x = inset + trackWidth * fraction
                // The thumb (and the gap M3 leaves around it) already marks whatever it covers.
                if (abs(x - thumbX) < inset) return@forEach
                drawLine(
                    color = tickColor,
                    start = Offset(x, (size.height - tickHeight) / 2f),
                    end = Offset(x, (size.height + tickHeight) / 2f),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
    }
}

/**
 * A chapter step: quiet on purpose, so it reads as navigation next to the scrub bar rather than
 * competing with the transport controls. Disabled at the ends of the book, where there is nowhere
 * to step to.
 */
@Composable
private fun ChapterSkip(
    icon: ImageVector,
    description: String,
    targetMs: Long?,
    onSeek: (Long) -> Unit,
) {
    IconButton(
        onClick = { targetMs?.let(onSeek) },
        enabled = targetMs != null,
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        modifier = Modifier.size(32.dp),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
