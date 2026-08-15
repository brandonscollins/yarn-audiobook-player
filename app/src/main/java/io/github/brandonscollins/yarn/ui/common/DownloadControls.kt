package io.github.brandonscollins.yarn.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.brandonscollins.yarn.data.download.DownloadState

/**
 * The one download entry in an overflow menu — Download, Cancel with the live track count, or
 * Remove, whichever [state] calls for. Callers close the menu in their own handlers.
 */
@Composable
fun DownloadMenuItem(
    state: DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    when {
        state.downloading ->
            DropdownMenuItem(
                text = { Text("Cancel download (${state.done}/${state.total})") },
                onClick = onCancel,
            )
        state.downloaded ->
            DropdownMenuItem(text = { Text("Remove download") }, onClick = onRemove)
        else ->
            DropdownMenuItem(text = { Text("Download") }, onClick = onDownload)
    }
}

/**
 * Tracks-done ring for a top bar, so a running download is visible without opening the menu.
 * Draws nothing when there's no download in flight.
 */
@Composable
fun DownloadProgress(
    state: DownloadState,
    modifier: Modifier = Modifier,
) {
    if (!state.downloading) return
    CircularProgressIndicator(
        progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        strokeWidth = 2.dp,
        modifier = modifier.padding(end = 4.dp).size(18.dp),
    )
}
