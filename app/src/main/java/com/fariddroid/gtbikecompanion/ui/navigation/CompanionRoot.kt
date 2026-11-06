package com.fariddroid.gtbikecompanion.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fariddroid.gtbikecompanion.BuildConfig
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.feature.connect.ConnectScreen
import com.fariddroid.gtbikecompanion.feature.more.DemoControlViewModel
import com.fariddroid.gtbikecompanion.feature.more.HelpTopicsScreen
import com.fariddroid.gtbikecompanion.feature.more.MoreScreen
import com.fariddroid.gtbikecompanion.feature.ride.RideRoute
import com.fariddroid.gtbikecompanion.feature.routes.RoutesRoute
import com.fariddroid.gtbikecompanion.feature.activities.ActivitiesRoute
import com.fariddroid.gtbikecompanion.feature.devices.DevicesRoute
import com.fariddroid.gtbikecompanion.feature.devices.PhoneScanRoute
import com.fariddroid.gtbikecompanion.feature.diagnostics.DiagnosticsRoute
import com.fariddroid.gtbikecompanion.feature.world.RidersRoute
import com.fariddroid.gtbikecompanion.feature.training.TrainingRoute
import com.fariddroid.gtbikecompanion.feature.settings.PcSettingsRoute
import com.fariddroid.gtbikecompanion.ui.theme.CompanionTheme

object Routes {
    const val CONNECT = "connect"
    const val RIDE = "ride"
    const val MORE = "more"
    const val HELP_TOPICS = "help_topics"
    const val ROUTES = "routes"
    const val PC_SETTINGS = "pc_settings"
    const val TRAINING = "training"
    const val DEVICES = "devices"
    const val PHONE_SCAN = "phone_scan"
    const val ACTIVITIES = "activities"
    const val RIDERS = "riders"
    const val DIAGNOSTICS = "diagnostics"
}

private data class TopLevel(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int)

/** The four destinations of plan §9: Ride, Routes (P3), Training (P6) and More. */
private val topLevel = listOf(
    TopLevel(Routes.RIDE, R.string.nav_ride, R.drawable.ic_nav_ride),
    TopLevel(Routes.ROUTES, R.string.nav_routes, R.drawable.ic_nav_routes),
    TopLevel(Routes.TRAINING, R.string.nav_training, R.drawable.ic_nav_training),
    TopLevel(Routes.MORE, R.string.nav_more, R.drawable.ic_nav_more),
)

@Composable
fun CompanionRoot(appViewModel: AppViewModel = hiltViewModel()) {
    val loaded by appViewModel.settings.collectAsStateWithLifecycle()
    val settings = loaded
    if (settings == null) {
        // Window background already matches the theme; nothing to draw until preferences are read.
        Box(Modifier.fillMaxSize())
        return
    }
    CompanionTheme(settings.theme) {
        val nav = rememberNavController()
        val start = remember { if (settings.onboardingDone) Routes.RIDE else Routes.CONNECT }
        val entry by nav.currentBackStackEntryAsState()
        val current = entry?.destination?.route
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (topLevel.any { it.route == current }) {
                    NavigationBar(modifier = Modifier.testTag("bottomBar")) {
                        topLevel.forEach { item ->
                            NavigationBarItem(
                                selected = current == item.route,
                                onClick = { nav.navigateTopLevel(item.route) },
                                icon = { Icon(painterResource(item.icon), contentDescription = null) },
                                label = { Text(stringResource(item.label)) },
                                modifier = Modifier.testTag("nav:${item.route}"),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(navController = nav, startDestination = start, modifier = Modifier.padding(padding)) {
                composable(Routes.CONNECT) {
                    val toRide = {
                        appViewModel.setOnboardingDone(true)
                        nav.navigate(Routes.RIDE) { popUpTo(nav.graph.id) { inclusive = true } }
                    }
                    ConnectScreen(onPaired = toRide, onUseDemo = toRide)
                }
                composable(Routes.RIDE) { RideRoute(onPairAgain = { nav.navigate(Routes.CONNECT) }, onOpenDevices = { nav.navigate(Routes.DEVICES) }) }
                composable(Routes.TRAINING) { TrainingRoute() }
                composable(Routes.DEVICES) { DevicesRoute(onOpenPhoneScan = { nav.navigate(Routes.PHONE_SCAN) }) }
                composable(Routes.PHONE_SCAN) { PhoneScanRoute() }
                composable(Routes.ACTIVITIES) { ActivitiesRoute() }
                composable(Routes.RIDERS) { RidersRoute() }
                composable(Routes.DIAGNOSTICS) { DiagnosticsRoute() }
                composable(Routes.ROUTES) { RoutesRoute() }
                composable(Routes.PC_SETTINGS) { PcSettingsRoute() }
                composable(Routes.MORE) {
                    MoreScreen(
                        settings = settings,
                        onTheme = appViewModel::setTheme,
                        onUnits = appViewModel::setUnits,
                        onOpenHelpTopics = { nav.navigate(Routes.HELP_TOPICS) },
                        onOpenConnection = { nav.navigate(Routes.CONNECT) },
                        onOpenPcSettings = { nav.navigate(Routes.PC_SETTINGS) },
                        onOpenDevices = { nav.navigate(Routes.DEVICES) },
                        onOpenActivities = { nav.navigate(Routes.ACTIVITIES) },
                        onOpenRiders = { nav.navigate(Routes.RIDERS) },
                        onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                        demoControl = if (BuildConfig.DEBUG) hiltViewModel<DemoControlViewModel>() else null,
                    )
                }
                composable(Routes.HELP_TOPICS) { HelpTopicsScreen() }
            }
        }
    }
}

/** Ride is the root of the main graph once onboarding is done; tabs never stack on each other. */
private fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(Routes.RIDE) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
