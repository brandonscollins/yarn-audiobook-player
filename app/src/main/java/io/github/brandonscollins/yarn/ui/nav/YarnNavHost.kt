package io.github.brandonscollins.yarn.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.brandonscollins.yarn.ui.detail.BookDetailScreen
import io.github.brandonscollins.yarn.ui.home.HomeScreen
import io.github.brandonscollins.yarn.ui.library.LibraryScreen
import io.github.brandonscollins.yarn.ui.onboarding.LibraryPickScreen
import io.github.brandonscollins.yarn.ui.onboarding.LoginScreen
import io.github.brandonscollins.yarn.ui.onboarding.ServerPickScreen
import io.github.brandonscollins.yarn.ui.player.MiniPlayerBar
import io.github.brandonscollins.yarn.ui.player.PlayerScreen
import io.github.brandonscollins.yarn.ui.player.PlayerViewModel
import io.github.brandonscollins.yarn.ui.settings.SettingsScreen

/** The one NavHost, plus the mini-player/bottom-nav chrome the PRD wants wrapped around it. */
@Composable
fun YarnApp(startDestination: String) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = viewModel()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val bookIdPlaying by playerViewModel.controller.bookId.collectAsState()

    val showMiniPlayer =
        bookIdPlaying != null &&
            currentRoute in setOf(Routes.HOME, Routes.LIBRARY, Routes.BOOK_DETAIL)
    val showBottomNav = currentRoute == Routes.HOME || currentRoute == Routes.LIBRARY

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
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
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
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.LIBRARY) {
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
                )
            }
            composable(Routes.PLAYER) {
                PlayerScreen(playerViewModel = playerViewModel, onBack = { navController.popBackStack() })
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
