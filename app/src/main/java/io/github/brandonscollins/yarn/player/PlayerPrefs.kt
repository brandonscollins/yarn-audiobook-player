package io.github.brandonscollins.yarn.player

import android.content.Context

/** SharedPreferences-backed player settings. One global speed is enough for P0. */
class PlayerPrefs(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    var speed: Float
        get() = prefs.getFloat(KEY_SPEED, 1f)
        set(value) = prefs.edit().putFloat(KEY_SPEED, value).apply()

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

    private companion object {
        const val KEY_SPEED = "speed"
        const val KEY_AUTO_SLEEP = "auto_sleep_enabled"
        const val KEY_WINDOW_START = "window_start_minutes"
        const val KEY_WINDOW_END = "window_end_minutes"
        const val KEY_DEFAULT_DURATION = "default_duration_min"
    }
}
