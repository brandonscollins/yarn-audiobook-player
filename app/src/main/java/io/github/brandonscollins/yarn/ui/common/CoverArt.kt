package io.github.brandonscollins.yarn.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.brandonscollins.yarn.settings.PlexPrefs
import java.net.URLEncoder

/**
 * Plex serves art through its own transcoder. Shape derived from chronicle-reference's
 * `PlexConfig.makeThumbUri`.
 */
fun thumbUri(
    prefs: PlexPrefs,
    thumbPath: String,
): String {
    if (thumbPath.isEmpty() || prefs.chosenServerUri.isEmpty()) return ""
    val token = prefs.serverToken.ifEmpty { prefs.accountToken }
    val encoded = URLEncoder.encode(thumbPath, "UTF-8")
    return "${prefs.chosenServerUri}/photo/:/transcode?width=400&height=400&url=$encoded&X-Plex-Token=$token"
}

/** Thin progress bar under a cover — shared by Home's hero card and the Library grid. */
@Composable
fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(3.dp),
    )
}
