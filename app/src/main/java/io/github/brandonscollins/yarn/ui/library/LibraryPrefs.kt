package io.github.brandonscollins.yarn.ui.library

import android.content.Context

/** Grid = covers; List = row with a thumbnail; ListCompact = row, no thumbnail. */
enum class ViewMode { Grid, List, ListCompact }

enum class SortMode { Title, RecentlyAdded, RecentlyPublished }

/**
 * Progress filter. "Not started" means no ledger row at all, not a row sitting at zero. Downloaded
 * is the odd one out — it reads the book's cache state, not the ledger. Persisted by ordinal, so
 * new cases go on the end.
 */
enum class FilterMode { All, InProgress, NotStarted, Finished, Downloaded }

/** SharedPreferences-backed library UI state — view mode, sort and filter, sticky across sessions. */
class LibraryPrefs(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)

    var viewMode: ViewMode
        get() = ViewMode.entries.getOrElse(prefs.getInt(KEY_VIEW_MODE, 0)) { ViewMode.Grid }
        set(value) = prefs.edit().putInt(KEY_VIEW_MODE, value.ordinal).apply()

    var sortMode: SortMode
        get() = SortMode.entries.getOrElse(prefs.getInt(KEY_SORT_MODE, 0)) { SortMode.Title }
        set(value) = prefs.edit().putInt(KEY_SORT_MODE, value.ordinal).apply()

    var filterMode: FilterMode
        get() = FilterMode.entries.getOrElse(prefs.getInt(KEY_FILTER_MODE, 0)) { FilterMode.All }
        set(value) = prefs.edit().putInt(KEY_FILTER_MODE, value.ordinal).apply()

    private companion object {
        const val KEY_VIEW_MODE = "view_mode"
        const val KEY_SORT_MODE = "sort_mode"
        const val KEY_FILTER_MODE = "filter_mode"
    }
}
