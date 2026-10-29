package dev.ashish.wristpet.mobile.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.ashish.wristpet.sync.PetCommand
import dev.ashish.wristpet.sync.PetSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Sends a command; the callback says whether a watch received it. */
typealias SendCommand = (PetCommand, (Boolean) -> Unit) -> Unit

private enum class Tab(val route: String, val label: String, val glyph: String) {
    PET("pet", "Pet", "♥"),
    GALLERY("gallery", "Gallery", "✦"),
    HISTORY("history", "History", "▮"),
    SETTINGS("settings", "Settings", "⚙"),
}

@Composable
fun PhoneApp(snapshots: Flow<PetSnapshot?>, watchReachable: Flow<Boolean>, send: SendCommand) {
    val snapshot by snapshots.collectAsStateWithLifecycle(initialValue = null)
    val reachable by watchReachable.collectAsStateWithLifecycle(initialValue = false)
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Every command reports back: the watch is the source of truth, so failures must be visible.
    val sendWithFeedback: SendCommand = { command, onResult ->
        send(command) { ok ->
            onResult(ok)
            if (!ok) scope.launch { snackbar.showSnackbar("Watch not reachable. Try again when it's nearby.") }
        }
    }

    WristPetTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = current?.destination?.route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Tab.PET.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(tab.glyph) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = Tab.PET.route, modifier = Modifier.padding(padding)) {
                composable(Tab.PET.route) { DashboardScreen(snapshot, reachable, sendWithFeedback) }
                composable(Tab.GALLERY.route) { GalleryScreen(snapshot, reachable, sendWithFeedback) }
                composable(Tab.HISTORY.route) { HistoryScreen(snapshot) }
                composable(Tab.SETTINGS.route) { SettingsScreen(snapshot, reachable, sendWithFeedback) }
            }
        }
    }
}
