package io.github.brandonscollins.yarn.ui.nav

/** Route constants — flat graph, no nesting. Onboarding steps are just routes like any other. */
object Routes {
    const val LOGIN = "onboarding/login"
    const val SERVERS = "onboarding/servers"
    const val LIBRARIES = "onboarding/libraries"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val BOOK_DETAIL = "book/{bookId}"

    fun bookDetail(bookId: Int) = "book/$bookId"
}
