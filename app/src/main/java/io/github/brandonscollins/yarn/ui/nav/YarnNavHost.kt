package io.github.brandonscollins.yarn.ui.nav

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.brandonscollins.yarn.ui.detail.BookDetailScreen
import io.github.brandonscollins.yarn.ui.home.HomeScreen
import io.github.brandonscollins.yarn.ui.home.RecentlyPlayedScreen
import io.github.brandonscollins.yarn.ui.library.LibraryScreen
import io.github.brandonscollins.yarn.ui.onboarding.LibraryPickScreen
import io.github.brandonscollins.yarn.ui.onboarding.LoginScreen
import io.github.brandonscollins.yarn.ui.onboarding.ServerPickScreen
import io.github.brandonscollins.yarn.ui.player.MiniPlayerBar
import io.github.brandonscollins.yarn.ui.player.PlayerScreen
import io.github.brandonscollins.yarn.ui.player.PlayerViewModel
import io.github.brandonscollins.yarn.ui.settings.SettingsScreen

/**
 * Kept in step with `MainActivity.ACTION_RESUME` and `res/xml/shortcuts.xml`; the constant there
 * is private and MainActivity exposes no observable "the shortcut fired" flag, so the intent
 * itself is the signal.
 */
private const val ACTION_RESUME = "io.github.brandonscollins.yarn.action.RESUME"

/**
 * MainActivity's launcher-shortcut handler starts the book; landing on the Player is the
 * navigation half of it, and only the NavHost has a controller to do it with. Home stays the
 * start destination — making Player the start leaves it alone on the back stack, where its back
 * arrow pops to nothing and blanks the host.
 */
@Composable
private fun ResumeShortcutEffect(navController: NavHostController) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    // Survives rotation, so a config change doesn't bounce the user back onto the Player.
    var handled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!handled && activity.intent?.action == ACTION_RESUME) {
            handled = true
            navController.navigate(Routes.PLAYER)
        }
    }
    // The activity is singleTop, so a shortcut tapped at a warm app never re-enters composition.
    DisposableEffect(activity) {
        val listener =
            Consumer<Intent> { intent ->
                if (intent.action == ACTION_RESUME) {
                    navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                }
            }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}

/** The one NavHost, plus the mini-player/bottom-nav chrome the PRD wants wrapped around it. */
@Composable
fun YarnApp(startDestination: String) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = viewModel()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val bookIdPlaying by playerViewModel.controller.bookId.collectAsState()

    val showMiniPlayer =
        bookIdPlaying != null &&
            currentRoute in
            setOf(
                Routes.HOME,
                Routes.LIBRARY,
                Routes.LIBRARY_SEARCH,
                Routes.BOOK_DETAIL,
                Routes.RECENTLY_PLAYED,
            )
    val showBottomNav =
        currentRoute == Routes.HOME ||
            currentRoute == Routes.LIBRARY ||
            currentRoute == Routes.LIBRARY_SEARCH

    ResumeShortcutEffect(navController)

    Scaffold(
        bottomBar = {
            Column {
                if (showMiniPlayer) {
                    MiniPlayerBar(
                        playerViewModel = playerViewModel,
                        onClick = { navController.navigate(Routes.PLAYER) },
                    )
                }
                if (showBottomNav) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentRoute == Routes.HOME,
                            onClick = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.HOME) { inclusive = true }
                                }
                            },
                            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                            label = { Text("Home") },
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.LIBRARY,
                            onClick = {
                                navController.navigate(Routes.LIBRARY) { launchSingleTop = true }
                            },
                            icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
                            label = { Text("Library") },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onSignedIn = {
                        navController.navigate(Routes.SERVERS) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.SERVERS) {
                ServerPickScreen(
                    onConnected = {
                        navController.navigate(Routes.LIBRARIES) {
                            popUpTo(Routes.SERVERS) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.LIBRARIES) {
                LibraryPickScreen(
                    onDone = { navController.navigate(Routes.HOME) { popUpTo(0) } },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    playerViewModel = playerViewModel,
                    onOpenBook = { bookId -> navController.navigate(Routes.bookDetail(bookId)) },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    onOpenRecentlyPlayed = { navController.navigate(Routes.RECENTLY_PLAYED) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.RECENTLY_PLAYED) {
                RecentlyPlayedScreen(
                    playerViewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenBook = { bookId -> navController.navigate(Routes.bookDetail(bookId)) },
                )
            }
            // Same screen; LibraryViewModel picks the query straight out of its SavedStateHandle.
            composable(
                Routes.LIBRARY_SEARCH,
                arguments = listOf(navArgument("query") { type = NavType.StringType }),
            ) {
                LibraryScreen(
                    onOpenBook = { bookId -> navController.navigate(Routes.bookDetail(bookId)) },
                )
            }
            composable(
                Routes.BOOK_DETAIL,
                arguments = listOf(navArgument("bookId") { type = NavType.IntType }),
            ) {
                BookDetailScreen(
                    playerViewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    onOpenAuthor = { author -> navController.navigate(Routes.librarySearch(author)) },
                )
            }
            composable(Routes.PLAYER) {
                PlayerScreen(
                    playerViewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { bookId -> navController.navigate(Routes.bookDetail(bookId)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSignedOut = {
                        navController.navigate(Routes.LOGIN) { popUpTo(0) }
                    },
                )
            }
        }
    }
}
