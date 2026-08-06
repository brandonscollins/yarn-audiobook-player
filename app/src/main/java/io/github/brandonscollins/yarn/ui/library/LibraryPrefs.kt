package io.github.brandonscollins.yarn.ui.library

import android.content.Context

/** Grid = covers; List = row with a thumbnail; ListCompact = row, no thumbnail. */
enum class ViewMode { Grid, List, ListCompact }

enum class SortMode { Title, RecentlyAdded, RecentlyPublished }

/** SharedPreferences-backed library UI state — view mode and sort, sticky across sessions. */
class LibraryPrefs(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)

    var viewMode: ViewMode
        get() = ViewMode.entries.getOrElse(prefs.getInt(KEY_VIEW_MODE, 0)) { ViewMode.Grid }
        set(value) = prefs.edit().putInt(KEY_VIEW_MODE, value.ordinal).apply()

    var sortMode: SortMode
        get() = SortMode.entries.getOrElse(prefs.getInt(KEY_SORT_MODE, 0)) { SortMode.Title }
        set(value) = prefs.edit().putInt(KEY_SORT_MODE, value.ordinal).apply()

    private companion object {
        const val KEY_VIEW_MODE = "view_mode"
        const val KEY_SORT_MODE = "sort_mode"
    }
}
