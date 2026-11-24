package com.essvocab.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    HOME("home", "学习", Icons.Rounded.Home),
    VOCAB("vocab", "词库", Icons.AutoMirrored.Rounded.MenuBook),
    PROFILE("profile", "我的", Icons.Rounded.Person),
}

@Composable
fun EssVocabApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var vocabRequestedTab by rememberSaveable { mutableIntStateOf(0) }
    var vocabRequestNonce by rememberSaveable { mutableLongStateOf(0L) }

    val showBottomBar = currentRoute == TopTab.HOME.route ||
        currentRoute == TopTab.VOCAB.route ||
        currentRoute == TopTab.PROFILE.route

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openVocabSubTab(tabIndex: Int) {
        vocabRequestedTab = tabIndex
        vocabRequestNonce += 1L
        navigateTopTab(TopTab.VOCAB.route)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    TopTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navigateTopTab(tab.route) },
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
                    onStartBookStudy = { navController.navigate("study/book") },
                    onStartExtraStudy = { navController.navigate("study/extra") },
                    onSwitchBook = { openVocabSubTab(0) },
                    onOpenDictionarySearch = { openVocabSubTab(1) },
                    onOpenNotebook = { openVocabSubTab(2) },
                )
            }

            composable(TopTab.VOCAB.route) {
                DictionaryScreen(
                    initialTab = vocabRequestedTab,
                    requestNonce = vocabRequestNonce,
                    onStartPackStudy = { packId ->
                        val target = if (packId.isNullOrBlank()) "pack:all" else "pack:$packId"
                        navController.navigate("study/$target")
                    },
                    onStartNotebookStudy = { navController.navigate("study/notebook") },
                )
            }

            composable(TopTab.PROFILE.route) {
                SettingsScreen()
            }

            composable(
                route = "study/{mode}",
                arguments = listOf(
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = "book"
                    },
                ),
            ) {
                StudyScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
