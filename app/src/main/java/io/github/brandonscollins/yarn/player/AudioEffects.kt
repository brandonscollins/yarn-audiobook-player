package io.github.brandonscollins.yarn.player

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/** Volume boost range in millibels — 0 (off) to +1200 mB / +12 dB. */
const val MAX_BOOST_MB = 1200

/** Speed limits. The UI may offer presets; the engine takes anything in this range. */
const val MIN_SPEED = 0.5f
const val MAX_SPEED = 3.0f

/** No device preset — the band levels are the user's own. */
const val EQ_PRESET_CUSTOM = -1

/** Session id 0 is `AUDIO_SESSION_ID_UNSET`: no AudioTrack yet, nothing to attach to. */
private const val SESSION_UNSET = 0

private const val TAG = "YarnAudioEffects"

fun coerceBoostMb(mB: Int): Int = mB.coerceIn(0, MAX_BOOST_MB)

fun coerceSpeed(speed: Float): Float = speed.coerceIn(MIN_SPEED, MAX_SPEED)

/** Band levels persist as csv — SharedPreferences has no short-array type. */
fun encodeBandLevels(levels: ShortArray): String = levels.joinToString(",")

fun decodeBandLevels(csv: String): ShortArray =
    if (csv.isBlank()) {
        ShortArray(0)
    } else {
        csv.split(',').mapNotNull { it.trim().toShortOrNull() }.toShortArray()
    }

/**
 * What this device's [Equalizer] offers. Static once the effects attach, null before that (a device
 * with no usable equalizer never publishes one).
 */
data class EqBandInfo(
    val bandCount: Int,
    val minLevelMb: Short,
    val maxLevelMb: Short,
    val centerFreqLabels: List<String>,
    val presetNames: List<String>,
)

/**
 * Effect state for the UI, published the same way [SleepState] publishes the sleep timer.
 *
 * ponytail: process-global StateFlows rather than session-extras IPC — service and UI share one
 * process. If the service ever moves to its own process, publish these via session extras.
 */
object EffectsState {
    val boostMb = MutableStateFlow(0)
    val eqEnabled = MutableStateFlow(false)
    val eqPreset = MutableStateFlow(EQ_PRESET_CUSTOM)
    val eqBandLevels = MutableStateFlow(ShortArray(0))
    val bandInfo = MutableStateFlow<EqBandInfo?>(null)
}

/**
 * `LoudnessEnhancer` + `Equalizer` on ExoPlayer's audio session, owned by [PlaybackService].
 *
 * Lifecycle: [attach] on every `onAudioSessionIdChanged` (the id changes when the AudioTrack is
 * recreated), [release] in the service's `onDestroy` before the player is released. Track
 * transitions inside one session don't touch the effects.
 *
 * audiofx constructors and setters throw on some devices and some sessions, so every call is
 * wrapped: a device with no usable effect chain plays unaffected audio instead of crashing.
 */
class AudioEffects(private val prefs: PlayerPrefs) {
    private var sessionId = SESSION_UNSET
    private var loudness: LoudnessEnhancer? = null
    private var eq: Equalizer? = null

    init {
        // Seed the flows from prefs so the UI shows the persisted settings before anything attaches.
        EffectsState.boostMb.value = prefs.boostMb
        EffectsState.eqEnabled.value = prefs.eqEnabled
        EffectsState.eqPreset.value = prefs.eqPreset
        EffectsState.eqBandLevels.value = prefs.eqBandLevels
    }

    /** Binds to [newSessionId], releasing whatever was bound to the old one. */
    fun attach(newSessionId: Int) {
        if (newSessionId == sessionId) return
        release()
        sessionId = newSessionId
        if (newSessionId == SESSION_UNSET) return
        loudness = quietly("LoudnessEnhancer(session)") { LoudnessEnhancer(newSessionId) }
        eq = quietly("Equalizer(session)") { Equalizer(0, newSessionId) }
        eq?.let { e -> readBandInfo(e)?.let { EffectsState.bandInfo.value = it } }
        applyBoost()
        applyEq()
    }

    fun release() {
        quietly("loudness.release") { loudness?.release() }
        quietly("eq.release") { eq?.release() }
        loudness = null
        eq = null
        sessionId = SESSION_UNSET
        // Otherwise a destroyed service leaves the last session's band info on screen — the EQ
        // sheet would show stale bands/presets for a device that no longer has an Equalizer.
        EffectsState.bandInfo.value = null
    }

    fun setBoost(mB: Int) {
        val value = coerceBoostMb(mB)
        prefs.boostMb = value
        EffectsState.boostMb.value = value
        applyBoost()
    }

    fun setEqEnabled(enabled: Boolean) {
        prefs.eqEnabled = enabled
        EffectsState.eqEnabled.value = enabled
        applyEq()
    }

    /** [index] < 0 means custom (keep the stored band levels). */
    fun setPreset(index: Int) {
        prefs.eqPreset = index
        EffectsState.eqPreset.value = index
        val e = eq
        if (index >= 0 && e != null) {
            quietly("usePreset") { e.usePreset(index.toShort()) }
            // Read the preset's levels back, so a later manual nudge starts from what's playing.
            readBandLevels(e)?.let(::storeBandLevels)
        }
        applyEq()
    }

    /** Any manual band change makes the setting custom. */
    fun setBand(
        band: Int,
        levelMb: Short,
    ) {
        val bandCount = EffectsState.bandInfo.value?.bandCount ?: (band + 1)
        val levels = EffectsState.eqBandLevels.value.copyOf(bandCount)
        if (band !in levels.indices) return
        levels[band] = levelMb
        quietly("setBandLevel") { eq?.setBandLevel(band.toShort(), levelMb) }
        storeBandLevels(levels)
        prefs.eqPreset = EQ_PRESET_CUSTOM
        EffectsState.eqPreset.value = EQ_PRESET_CUSTOM
    }

    private fun applyBoost() {
        val l = loudness ?: return
        val mB = prefs.boostMb
        quietly("apply boost") {
            l.setTargetGain(mB)
            // A neutral setting leaves the effect disabled — nothing in the signal path.
            l.enabled = mB > 0
        }
    }

    private fun applyEq() {
        val e = eq ?: return
        quietly("apply eq") {
            val preset = prefs.eqPreset
            if (preset >= 0) {
                e.usePreset(preset.toShort())
            } else {
                val bands = e.numberOfBands
                prefs.eqBandLevels.forEachIndexed { band, level ->
                    if (band < bands) e.setBandLevel(band.toShort(), level)
                }
            }
            e.enabled = prefs.eqEnabled
        }
    }

    private fun storeBandLevels(levels: ShortArray) {
        prefs.eqBandLevels = levels
        EffectsState.eqBandLevels.value = levels
    }

    private fun readBandLevels(e: Equalizer): ShortArray? =
        quietly("read band levels") {
            ShortArray(e.numberOfBands.toInt()) { e.getBandLevel(it.toShort()) }
        }

    private fun readBandInfo(e: Equalizer): EqBandInfo? =
        quietly("read band info") {
            val range = e.bandLevelRange
            val bands = e.numberOfBands.toInt()
            EqBandInfo(
                bandCount = bands,
                minLevelMb = range[0],
                maxLevelMb = range[1],
                centerFreqLabels = (0 until bands).map { freqLabel(e.getCenterFreq(it.toShort())) },
                presetNames =
                    (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) },
            )
        }
}

/** Center frequencies come back in milliHertz. */
private fun freqLabel(centerFreqMilliHz: Int): String {
    val hz = centerFreqMilliHz / 1000
    return if (hz >= 1000) {
        String.format(Locale.US, "%.1f", hz / 1000f).trimEnd('0').trimEnd('.') + "kHz"
    } else {
        "${hz}Hz"
    }
}

private inline fun <T> quietly(
    what: String,
    block: () -> T,
): T? =
    try {
        block()
    } catch (e: RuntimeException) {
        // Includes UnsupportedOperationException/IllegalStateException from audiofx constructors.
        Log.w(TAG, "audiofx $what failed; degrading to no effect", e)
        null
    }
