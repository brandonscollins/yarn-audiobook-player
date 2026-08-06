package io.github.brandonscollins.yarn.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LETTERS = ('A'..'Z').toList()

/**
 * Slim right-edge fast-scroll rail. Drag or tap maps y-position to a letter and reports it via
 * [onLetterSelected]; letters not in [availableLetters] render dimmed (the caller decides what
 * "nearest" means when one of those is picked). Shows a floating bubble with the current letter
 * while the finger is down.
 */
@Composable
fun AlphabetRail(
    availableLetters: Set<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }

    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier.fillMaxHeight().width(20.dp).pointerInput(Unit) {
                    fun letterAt(y: Float): Char {
                        val rowHeight = size.height / LETTERS.size.toFloat()
                        val index = (y / rowHeight).toInt().coerceIn(0, LETTERS.lastIndex)
                        return LETTERS[index]
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        dragging = true
                        var letter = letterAt(down.position.y)
                        activeLetter = letter
                        onLetterSelected(letter)
                        drag(down.id) { change ->
                            val next = letterAt(change.position.y)
                            if (next != letter) {
                                letter = next
                                activeLetter = next
                                onLetterSelected(next)
                            }
                            change.consume()
                        }
                        dragging = false
                        activeLetter = null
                    }
                },
        ) {
            LETTERS.forEach { letter ->
                Text(
                    letter.toString(),
                    fontSize = 10.sp,
                    fontWeight = if (letter == activeLetter) FontWeight.Bold else FontWeight.Normal,
                    color =
                        when {
                            letter == activeLetter -> MaterialTheme.colorScheme.primary
                            letter in availableLetters -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        activeLetter?.let { letter ->
            if (dragging) {
                // Gold bubble, serif letter — the fast-scroll equivalent of a thumb-index.
                Box(
                    modifier =
                        Modifier.align(Alignment.CenterEnd).offset(x = (-44).dp).size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        letter.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}
