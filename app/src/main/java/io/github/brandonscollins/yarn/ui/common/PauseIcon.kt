package io.github.brandonscollins.yarn.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * `Icons.Filled.Pause` isn't in material-icons-core (only the extended set has it, and we're not
 * adding that dependency for one glyph) — two bars read as "pause" well enough.
 */
@Composable
fun PauseGlyph(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(LocalContentColor.current))
        Box(Modifier.width(5.dp).fillMaxHeight().background(LocalContentColor.current))
    }
}
