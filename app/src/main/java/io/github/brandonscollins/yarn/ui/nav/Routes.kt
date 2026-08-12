package io.github.brandonscollins.yarn.ui.nav

import android.net.Uri

/** Route constants — flat graph, no nesting. Onboarding steps are just routes like any other. */
object Routes {
    const val LOGIN = "onboarding/login"
    const val SERVERS = "onboarding/servers"
    const val LIBRARIES = "onboarding/libraries"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val RECENTLY_PLAYED = "recently-played"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val BOOK_DETAIL = "book/{bookId}"

    /**
     * The library with its local search pre-filled — what tapping an author lands on. A second
     * route rather than an optional argument on [LIBRARY], because the bottom nav and the
     * mini-player compare `currentRoute` against these constants by equality.
     */
    const val LIBRARY_SEARCH = "library/{query}"

    fun bookDetail(bookId: Int) = "book/$bookId"

    /** [query] is a raw author or title; it becomes one path segment, so it has to be encoded. */
    fun librarySearch(query: String) = "library/${Uri.encode(query)}"
}
