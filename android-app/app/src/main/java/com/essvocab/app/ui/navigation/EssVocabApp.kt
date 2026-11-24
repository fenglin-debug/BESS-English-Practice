package com.essvocab.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.essvocab.app.ui.dictionary.DictionaryScreen
import com.essvocab.app.ui.home.HomeScreen
import com.essvocab.app.ui.settings.SettingsScreen
import com.essvocab.app.ui.study.StudyScreen

private enum class TopTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "首页", Icons.Rounded.Home),
    DICTIONARY("dictionary", "词表", Icons.AutoMirrored.Rounded.MenuBook),
    SETTINGS("settings", "设置", Icons.Rounded.Settings),
}

@Composable
fun EssVocabApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute == TopTab.HOME.route ||
        currentRoute?.startsWith(TopTab.DICTIONARY.route) == true ||
        currentRoute == TopTab.SETTINGS.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopTab.entries.forEach { tab ->
                        val selected = if (tab == TopTab.DICTIONARY) {
                            currentRoute?.startsWith(TopTab.DICTIONARY.route) == true
                        } else {
                            currentRoute == tab.route
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopTab.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopTab.HOME.route) {
                HomeScreen(
                    onStartStudy = { packId ->
                        val target = packId ?: "all"
                        navController.navigate("study/$target")
                    },
                    onOpenPackDictionary = { packId ->
                        navController.navigate("dictionary?packId=$packId")
                    },
                    onOpenSettings = {
                        navController.navigate(TopTab.SETTINGS.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            composable(
                route = "dictionary?packId={packId}",
                arguments = listOf(
                    navArgument("packId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                DictionaryScreen(initialPackId = entry.arguments?.getString("packId"))
            }

            composable(TopTab.SETTINGS.route) {
                SettingsScreen()
            }

            composable(
                route = "study/{packId}",
                arguments = listOf(
                    navArgument("packId") {
                        type = NavType.StringType
                        defaultValue = "all"
                    },
                ),
            ) {
                StudyScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
