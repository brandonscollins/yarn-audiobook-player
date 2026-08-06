package io.github.brandonscollins.yarn.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.ui.common.PauseGlyph
import io.github.brandonscollins.yarn.ui.common.thumbUri

/** Tiny bar shown above the bottom nav on Home/Library/Book detail whenever a session exists. */
@Composable
fun MiniPlayerBar(
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember(context) { PlexGraph.db(context) }
    val prefs = remember(context) { PlexGraph.prefs(context) }
    val controller = playerViewModel.controller
    val bookId by controller.bookId.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val book by produceState<Audiobook?>(initialValue = null, bookId) {
        value = null
        bookId?.let { id -> db.bookDao().getBook(id).collect { value = it } }
    }

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = book?.let { thumbUri(prefs, it.thumbPath) },
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
            )
            Text(
                text = book?.title.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            IconButton(onClick = { if (isPlaying) controller.pause() else controller.play() }) {
                if (isPlaying) {
                    PauseGlyph(modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                }
            }
        }
    }
}
