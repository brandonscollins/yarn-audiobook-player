package io.github.brandonscollins.yarn.ui.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.brandonscollins.yarn.player.EQ_PRESET_CUSTOM
import io.github.brandonscollins.yarn.player.MAX_BOOST_MB
import io.github.brandonscollins.yarn.player.MAX_SPEED
import io.github.brandonscollins.yarn.player.MIN_SPEED
import io.github.brandonscollins.yarn.player.PlayerController
import kotlin.math.abs
import kotlin.math.roundToInt

private val SPEED_PRESETS = listOf(0.5f, 1f, 1.5f, 2f, 3f)

/** ± steps land on 0.05 boundaries no matter where the slider left the value. */
private fun stepSpeed(
    from: Float,
    steps: Int,
) = (((from * 20f).roundToInt() + steps) / 20f).coerceIn(MIN_SPEED, MAX_SPEED)

/** The big readout: always two decimals, like the reference. */
private fun speedReadout(speed: Float) = "%.2fx".format(speed)

/** Whole dB. The readout has no room for half-steps, so this is also what gets applied. */
private fun boostDb(mB: Float) = (mB / 100f).roundToInt()

/**
 * Local value for a slider whose committed value round-trips through the service.
 *
 * Dragging updates this and nothing else; the commit happens once, on release. Otherwise every pixel
 * of drag is a session-command IPC plus a `SharedPreferences` write, and the asynchronous echo of
 * each commit arrives mid-gesture and fights the finger. Same shape as the Player screen's scrub
 * slider; [committed] re-seeds it whenever the real value changes (including our own commit landing).
 */
@Composable
private fun rememberDragValue(committed: Float): MutableState<Float> {
    val state = remember { mutableStateOf(committed) }
    LaunchedEffect(committed) { state.value = committed }
    return state
}

/** Chip labels: 0.5x, 1x, 1.5x. */
private fun speedLabel(speed: Float) = "%.2f".format(speed).trimEnd('0').trimEnd('.') + "x"

/**
 * Speed sheet, straight off the owner's reference: huge serif readout, −/slider/+ row, preset chips.
 * The slider is continuous (the engine takes anything in 0.5..3.0); the chips snap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val shown = rememberDragValue(speed.coerceIn(MIN_SPEED, MAX_SPEED))
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Playback speed",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                speedReadout(shown.value),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkCircleButton(Icons.Filled.Remove, "Slower") {
                    onSpeedChange(stepSpeed(shown.value, -1))
                }
                Slider(
                    value = shown.value,
                    onValueChange = { shown.value = it },
                    onValueChangeFinished = { onSpeedChange(shown.value) },
                    valueRange = MIN_SPEED..MAX_SPEED,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                InkCircleButton(Icons.Filled.Add, "Faster") {
                    onSpeedChange(stepSpeed(shown.value, 1))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SPEED_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = abs(shown.value - preset) < 0.001f,
                        onClick = { onSpeedChange(preset) },
                        label = { Text(speedLabel(preset)) },
                        shape = CircleShape,
                    )
                }
            }
        }
    }
}

/**
 * Volume boost + equalizer, same visual language as the speed sheet. On a device whose audiofx
 * chain never produced a usable [android.media.audiofx.Equalizer] the boost half still works and the
 * EQ half says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqSheet(
    controller: PlayerController,
    onDismiss: () -> Unit,
) {
    val boostMb by controller.boostMb.collectAsState()
    val eqEnabled by controller.eqEnabled.collectAsState()
    val eqPreset by controller.eqPreset.collectAsState()
    val bandLevels by controller.eqBandLevels.collectAsState()
    val bandInfo by controller.eqBandInfo.collectAsState()
    val shownBoost = rememberDragValue(boostMb.toFloat())

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Volume boost",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "+${boostDb(shownBoost.value)} dB",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Slider(
                value = shownBoost.value,
                onValueChange = { shownBoost.value = it },
                onValueChangeFinished = { controller.setBoost(boostDb(shownBoost.value) * 100) },
                valueRange = 0f..MAX_BOOST_MB.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            val info = bandInfo
            if (info == null) {
                Text(
                    "Equalizer unavailable on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Equalizer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = eqEnabled, onCheckedChange = controller::setEqEnabled)
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                info.presetNames.forEachIndexed { index, name ->
                    FilterChip(
                        selected = eqPreset == index,
                        onClick = { controller.setEqPreset(index) },
                        label = { Text(name) },
                        shape = CircleShape,
                    )
                }
                FilterChip(
                    selected = eqPreset == EQ_PRESET_CUSTOM,
                    onClick = { controller.setEqPreset(EQ_PRESET_CUSTOM) },
                    label = { Text("Custom") },
                    shape = CircleShape,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                (0 until info.bandCount).forEach { band ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VerticalSlider(
                            value = bandLevels.getOrElse(band) { 0 }.toFloat(),
                            valueRange = info.minLevelMb.toFloat()..info.maxLevelMb.toFloat(),
                            onCommit = { controller.setEqBand(band, it.roundToInt().toShort()) },
                        )
                        Text(
                            info.centerFreqLabels.getOrElse(band) { "" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Ink-filled circle with a gold glyph — the reference's ± buttons. */
@Composable
private fun InkCircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
    ) {
        Icon(icon, contentDescription = description)
    }
}

/**
 * A [Slider] turned on its side. Compose has no vertical slider, and one rotated layout is cheaper
 * than a hand-rolled drag gesture with its own accessibility story.
 */
@Composable
private fun VerticalSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
) {
    val shown = rememberDragValue(value.coerceIn(valueRange.start, valueRange.endInclusive))
    Box(modifier = Modifier.width(56.dp).height(160.dp), contentAlignment = Alignment.Center) {
        Slider(
            value = shown.value,
            onValueChange = { shown.value = it },
            onValueChangeFinished = { onCommit(shown.value) },
            valueRange = valueRange,
            modifier =
                Modifier
                    .graphicsLayer(rotationZ = 270f, transformOrigin = TransformOrigin(0f, 0f))
                    .layout { measurable, constraints ->
                        val placeable =
                            measurable.measure(
                                Constraints(
                                    minWidth = constraints.minHeight,
                                    maxWidth = constraints.maxHeight,
                                    minHeight = constraints.minWidth,
                                    maxHeight = constraints.maxWidth,
                                ),
                            )
                        layout(placeable.height, placeable.width) {
                            placeable.place(-placeable.width, 0)
                        }
                    },
        )
    }
}
