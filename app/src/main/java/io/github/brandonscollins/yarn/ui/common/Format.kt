package io.github.brandonscollins.yarn.ui.common

/** h:mm:ss, or mm:ss under an hour. */
fun formatDuration(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** mm:ss — the sleep-timer chip. */
fun formatMmSs(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/**
 * "4h 20m left" — the coarse label Home, book detail and the mini player all share. Always rounds
 * down, so it never promises less listening than there is, and collapses the last minute into
 * "under a minute left" rather than counting seconds at someone.
 */
fun formatRemaining(ms: Long): String {
    val totalMin = ms.coerceAtLeast(0) / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        totalMin < 1 -> "under a minute left"
        h == 0L -> "${m}m left"
        m == 0L -> "${h}h left"
        else -> "${h}h ${m}m left"
    }
}

private val LEADING_ARTICLES = listOf("The ", "A ", "An ")

/** Strips a leading "The "/"A "/"An " — used for alphabetical sort and A-Z rail bucketing. */
fun sortTitle(title: String): String {
    for (article in LEADING_ARTICLES) {
        if (title.startsWith(article, ignoreCase = true)) return title.substring(article.length)
    }
    return title
}
