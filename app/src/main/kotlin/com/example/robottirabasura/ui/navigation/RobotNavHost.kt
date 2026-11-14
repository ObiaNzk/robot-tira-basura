package com.example.robottirabasura.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.robottirabasura.R
import com.example.robottirabasura.ui.components.ConfirmDialog
import com.example.robottirabasura.ui.components.StatusChip
import com.example.robottirabasura.ui.components.label
import com.example.robottirabasura.ui.components.tone
import com.example.robottirabasura.ui.screens.alerts.AlertsRoute
import com.example.robottirabasura.ui.screens.history.HistoryRoute
import com.example.robottirabasura.ui.screens.mission.MissionSetupRoute
import com.example.robottirabasura.ui.screens.select.RobotSelectRoute
import com.example.robottirabasura.ui.screens.simulator.SimulatorRoute
import com.example.robottirabasura.ui.screens.supervision.SupervisionRoute
import kotlinx.coroutines.launch

private enum class Tab(val destination: Any, val labelRes: Int, val icon: ImageVector) {
    SUPERVISION(SupervisionDestination, R.string.tab_supervision, Icons.Default.Dashboard),
    MISSION(MissionDestination, R.string.tab_mission, Icons.Default.QrCodeScanner),
    ALERTS(AlertsDestination, R.string.tab_alerts, Icons.Default.Notifications),
    HISTORY(HistoryDestination, R.string.tab_history, Icons.AutoMirrored.Filled.List),
    SIMULATOR(SimulatorDestination, R.string.tab_simulator, Icons.Default.Science),
}

private fun NavDestination?.tab(): Tab? = when {
    this == null -> null
    hasRoute<SupervisionDestination>() -> Tab.SUPERVISION
    hasRoute<MissionDestination>() -> Tab.MISSION
    hasRoute<AlertsDestination>() -> Tab.ALERTS
    hasRoute<HistoryDestination>() -> Tab.HISTORY
    hasRoute<SimulatorDestination>() -> Tab.SIMULATOR
    else -> null
}

/** App shell: top bar with the link status and bottom tabs, shown once a robot is selected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RobotNavHost(modifier: Modifier = Modifier, shell: AppShellViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val shellState by shell.uiState.collectAsStateWithLifecycle()
    val tab = navController.currentBackStackEntryAsState().value?.destination.tab()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (tab != null) {
                TopAppBar(
                    title = {
                        Column {
                            Text(shellState.robotAlias ?: stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                            Text(
                                shellState.ageSeconds?.let { stringResource(R.string.link_age, it) } ?: stringResource(R.string.link_never),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    actions = {
                        StatusChip(stringResource(shellState.link.label), shellState.link.tone)
                        IconButton(onClick = { confirmDisconnect = true }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.action_disconnect))
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (tab != null) {
                NavigationBar {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { navController.openTab(item.destination) },
                            icon = {
                                if (item == Tab.ALERTS && shellState.openAlerts > 0) {
                                    BadgedBox(badge = { Badge { Text(shellState.openAlerts.toString()) } }) {
                                        Icon(item.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(item.icon, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(navController, startDestination = RobotSelectDestination, modifier = Modifier.padding(innerPadding)) {
            composable<RobotSelectDestination> {
                RobotSelectRoute(onOpenSupervision = {
                    navController.navigate(SupervisionDestination) {
                        popUpTo(RobotSelectDestination) { inclusive = true }
                    }
                })
            }
            composable<SupervisionDestination> { SupervisionRoute() }
            composable<MissionDestination> { MissionSetupRoute(onOpenSupervision = { navController.openTab(SupervisionDestination) }) }
            composable<AlertsDestination> { AlertsRoute() }
            composable<HistoryDestination> { HistoryRoute() }
            composable<SimulatorDestination> {
                SimulatorRoute(onMessage = { scope.launch { snackbar.showSnackbar(it) } })
            }
        }
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = stringResource(R.string.disconnect_title),
            body = stringResource(R.string.disconnect_body),
            onConfirm = {
                confirmDisconnect = false
                shell.onDisconnect()
                navController.navigate(RobotSelectDestination) { popUpTo(0) { inclusive = true } }
            },
            onDismiss = { confirmDisconnect = false },
        )
    }
}

/** Supervision is the root once a robot is selected (the selection screen is popped), so tabs pop up to it. */
private fun NavController.openTab(destination: Any) {
    navigate(destination) {
        popUpTo<SupervisionDestination> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
