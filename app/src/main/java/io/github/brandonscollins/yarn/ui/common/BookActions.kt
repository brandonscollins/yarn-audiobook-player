package io.github.brandonscollins.yarn.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.brandonscollins.yarn.data.download.DownloadState
import io.github.brandonscollins.yarn.data.download.Downloads
import io.github.brandonscollins.yarn.data.model.Audiobook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** The book whose long-press menu is open, if any. One sheet for the whole app, hosted in YarnApp. */
private val longPressed = MutableStateFlow<Audiobook?>(null)

/**
 * Tap does [onClick]; long-press opens the download sheet for [book]. Use in place of `clickable`
 * on anything that shows a book.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bookClickable(
    book: Audiobook,
    onClick: () -> Unit,
): Modifier = combinedClickable(onClick = onClick, onLongClick = { longPressed.value = book })

/** Download / Cancel / Remove for the long-pressed book. Placed once, at the app root. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookActionsSheet() {
    // Above the early return, so closing the sheet doesn't cancel a Remove still deleting files.
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val book by longPressed.collectAsState()
    val current = book ?: return
    val state by produceState(DownloadState(), current.id) {
        Downloads.observe(context, current.id).collect { value = it }
    }
    val close = { longPressed.value = null }

    ModalBottomSheet(onDismissRequest = close) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                current.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            DownloadMenuItem(
                state = state,
                onDownload = {
                    Downloads.start(context, current.id)
                    close()
                },
                onCancel = {
                    Downloads.cancel(context, current.id)
                    close()
                },
                onRemove = {
                    scope.launch { Downloads.remove(context, current.id) }
                    close()
                },
            )
        }
    }
}
