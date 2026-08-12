package io.github.brandonscollins.yarn.player

import android.content.Context

/** SharedPreferences-backed player settings. */
class PlayerPrefs(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    /** The fallback speed: what a book with no remembered speed of its own starts at. */
    var speed: Float
        get() = prefs.getFloat(KEY_SPEED, 1f)
        set(value) = prefs.edit().putFloat(KEY_SPEED, value).apply()

    /**
     * Per-book speed, so fiction at 1.2x and nonfiction at 1.8x each stick. A preference, not
     * library data, which is why it lives here and not in a Room column.
     *
     * ponytail: one key per book, never pruned — a few bytes per book listened to, invisible next
     * to the Room cache. Prune alongside the ledger row if a library ever gets big enough to care.
     */
    fun speedFor(bookId: Int): Float = prefs.getFloat("$KEY_SPEED_BOOK$bookId", speed)

    fun setSpeedFor(
        bookId: Int,
        value: Float,
    ) = prefs.edit().putFloat("$KEY_SPEED_BOOK$bookId", value).apply()

    /** Rewind-on-resume: [REWIND_OFF], [REWIND_FIXED] or [REWIND_SMART]. */
    var rewindMode: Int
        get() = prefs.getInt(KEY_REWIND_MODE, REWIND_OFF)
        set(value) = prefs.edit().putInt(KEY_REWIND_MODE, value).apply()

    /** How far [REWIND_FIXED] jumps back. */
    var fixedRewindSec: Int
        get() = prefs.getInt(KEY_REWIND_FIXED_SEC, 30)
        set(value) = prefs.edit().putInt(KEY_REWIND_FIXED_SEC, value).apply()

    /** Auto sleep window — PRD "Sleep-window spec". */
    var autoSleepEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SLEEP, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SLEEP, value).apply()

    /** Minutes since midnight; the window may cross midnight (21:30 → 06:00). */
    var windowStartMinutesOfDay: Int
        get() = prefs.getInt(KEY_WINDOW_START, 21 * 60 + 30)
        set(value) = prefs.edit().putInt(KEY_WINDOW_START, value).apply()

    var windowEndMinutesOfDay: Int
        get() = prefs.getInt(KEY_WINDOW_END, 6 * 60)
        set(value) = prefs.edit().putInt(KEY_WINDOW_END, value).apply()

    var defaultDurationMin: Int
        get() = prefs.getInt(KEY_DEFAULT_DURATION, 15)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_DURATION, value).apply()

    /** Audio effects — applied whenever [AudioEffects] (re)attaches to an audio session. */
    var boostMb: Int
        get() = prefs.getInt(KEY_BOOST_MB, 0)
        set(value) = prefs.edit().putInt(KEY_BOOST_MB, value).apply()

    var eqEnabled: Boolean
        get() = prefs.getBoolean(KEY_EQ_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_EQ_ENABLED, value).apply()

    /** Index into the device's preset list, or [EQ_PRESET_CUSTOM] for [eqBandLevels]. */
    var eqPreset: Int
        get() = prefs.getInt(KEY_EQ_PRESET, EQ_PRESET_CUSTOM)
        set(value) = prefs.edit().putInt(KEY_EQ_PRESET, value).apply()

    /** Per-band levels in millibels; empty until the user touches a band. */
    var eqBandLevels: ShortArray
        get() = decodeBandLevels(prefs.getString(KEY_EQ_BANDS, "").orEmpty())
        set(value) = prefs.edit().putString(KEY_EQ_BANDS, encodeBandLevels(value)).apply()

    private companion object {
        const val KEY_SPEED = "speed"
        const val KEY_SPEED_BOOK = "speed_book_"
        const val KEY_REWIND_MODE = "rewind_mode"
        const val KEY_REWIND_FIXED_SEC = "rewind_fixed_sec"
        const val KEY_AUTO_SLEEP = "auto_sleep_enabled"
        const val KEY_WINDOW_START = "window_start_minutes"
        const val KEY_WINDOW_END = "window_end_minutes"
        const val KEY_DEFAULT_DURATION = "default_duration_min"
        const val KEY_BOOST_MB = "boost_mb"
        const val KEY_EQ_ENABLED = "eq_enabled"
        const val KEY_EQ_PRESET = "eq_preset"
        const val KEY_EQ_BANDS = "eq_band_levels"
    }
}
