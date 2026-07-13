package com.example.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.PillPalBottomNavBar
import com.example.ui.components.PillPalTopStatusBar
import com.example.ui.screens.auth.CaregiverSignInScreen
import com.example.ui.screens.auth.CreateProfileScreen
import com.example.ui.screens.auth.VerifyEmailOtpScreen
import com.example.ui.screens.hub.ControlsView
import com.example.ui.screens.hub.HistoryView
import com.example.ui.screens.hub.HomeDashboard
import com.example.ui.screens.hub.ScheduleHub
import com.example.ui.screens.onboarding.ConnectWifiScreen
import com.example.ui.screens.onboarding.NoDeviceScreen
import com.example.ui.screens.onboarding.PairedSuccessScreen
import com.example.ui.screens.onboarding.PatientProfileScreen
import com.example.ui.screens.onboarding.ScheduleSetupScreen
import com.example.ui.viewmodel.ControlsViewModel
import com.example.ui.viewmodel.DeviceStatusViewModel
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.OnboardingViewModel
import com.example.ui.viewmodel.ScheduleViewModel

@Composable
fun PillPalNavGraph() {
    val navController = rememberNavController()

    // Boot gate: resolves once Supabase Auth's session restoration settles,
    // same as the old ViewModel's init{} block — sign_in renders first
    // (matching prior behavior), then this redirects onward if a session
    // and/or partially-finished onboarding already exists.
    val bootViewModel: OnboardingViewModel = hiltViewModel()
    LaunchedEffect(Unit) {
        val destination = bootViewModel.resolveStartDestination()
        if (destination != Routes.SIGN_IN) {
            navController.navigate(destination) {
                popUpTo(Routes.SIGN_IN) { inclusive = true }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
    NavHost(navController = navController, startDestination = Routes.ONBOARDING_GRAPH) {
        navigation(startDestination = Routes.SIGN_IN, route = Routes.ONBOARDING_GRAPH) {
            composable(Routes.SIGN_IN) { entry ->
                val viewModel = onboardingViewModel(entry, navController)
                ObserveOnboardingNavigation(viewModel, navController)
                CaregiverSignInScreen(viewModel, navController)
            }
            composable(Routes.CREATE_PROFILE) { entry ->
                val viewModel = onboardingViewModel(entry, navController)
                ObserveOnboardingNavigation(viewModel, navController)
                CreateProfileScreen(viewModel, navController)
            }
            composable(Routes.VERIFY_EMAIL_OTP) { entry ->
                val viewModel = onboardingViewModel(entry, navController)
                ObserveOnboardingNavigation(viewModel, navController)
                VerifyEmailOtpScreen(viewModel, navController)
            }
            composable(Routes.NO_DEVICE) { entry ->
                val viewModel = onboardingViewModel(entry, navController)
                ObserveOnboardingNavigation(viewModel, navController)
                NoDeviceScreen(viewModel, navController)
            }
            composable(Routes.CONNECT_WIFI) { entry ->
                val viewModel = onboardingViewModel(entry, navController)
                ObserveOnboardingNavigation(viewModel, navController)
                ConnectWifiScreen(viewModel, navController)
            }
            composable(Routes.PAIRED_SUCCESS) { entry ->
                val viewModel = onboardingViewModel(entry, navController)
                ObserveOnboardingNavigation(viewModel, navController)
                PairedSuccessScreen(viewModel, navController)
            }
            composable(Routes.PATIENT_PROFILE) { entry ->
                val viewModel = onboardingViewModel(entry, navController)
                ObserveOnboardingNavigation(viewModel, navController)
                PatientProfileScreen(viewModel)
            }
            composable(Routes.SCHEDULE_SETUP) { entry ->
                val viewModel = onboardingViewModel(entry, navController)
                ObserveOnboardingNavigation(viewModel, navController)
                ScheduleSetupScreen(viewModel)
            }
        }

        composable(Routes.MAIN_HUB) {
            HubScreen(rootNavController = navController)
        }
    }
    }
}

// OnboardingViewModel is scoped to the "onboarding" graph's own back stack
// entry (not the leaf route) so the same instance — and its in-progress
// sign-up/pairing/profile state — survives across all nine screens in the
// flow, and is torn down only when the graph itself is popped (reaching the
// hub, or signing back out to sign_in from scratch).
@Composable
private fun onboardingViewModel(entry: NavBackStackEntry, navController: NavController): OnboardingViewModel {
    val parentEntry = remember(entry) { navController.getBackStackEntry(Routes.ONBOARDING_GRAPH) }
    return hiltViewModel(parentEntry)
}

// One-time navigation events set by OnboardingViewModel (sign-in/up success,
// OTP verified, wifi credentials sent, device claimed, profile saved,
// onboarding completed) — observed here instead of duplicated per-screen so
// each screen only has to trigger the mutation, not know where it leads.
@Composable
private fun ObserveOnboardingNavigation(viewModel: OnboardingViewModel, navController: NavController) {
    val navigationEvent by viewModel.navigationEvent.collectAsState()
    LaunchedEffect(navigationEvent) {
        val destination = navigationEvent ?: return@LaunchedEffect
        viewModel.consumeNavigationEvent()
        if (destination == Routes.MAIN_HUB) {
            navController.navigate(Routes.MAIN_HUB) {
                popUpTo(Routes.ONBOARDING_GRAPH) { inclusive = true }
            }
        } else {
            navController.navigate(destination)
        }
    }
}

// Hosts the 4 hub tabs behind their own inner NavHost + bottom nav, plus the
// top status bar — HubSessionStore is a @Singleton, so DeviceStatusViewModel
// and each tab's ViewModel all read the same underlying data regardless of
// default per-route Hilt scoping; no special graph scoping needed here.
@Composable
private fun HubScreen(rootNavController: NavController) {
    val hubNavController = rememberNavController()
    val deviceStatusViewModel: DeviceStatusViewModel = hiltViewModel()
    val isSyncing by deviceStatusViewModel.isSyncing.collectAsState()
    val isOnline by deviceStatusViewModel.isOnline.collectAsState()
    val batteryLevel by deviceStatusViewModel.batteryLevel.collectAsState()

    // The FCM token is always registered (DeviceStatusViewModel's init{}),
    // regardless of this permission — it only gates whether the OS is
    // allowed to actually display the missed-dose banner (Android 13+).
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val hubBackStackEntry by hubNavController.currentBackStackEntryAsState()
    val currentRoute = hubBackStackEntry?.destination?.route

    Scaffold(
        topBar = {
            PillPalTopStatusBar(
                isSyncing = isSyncing,
                isOnline = isOnline,
                batteryLevel = batteryLevel,
                onSyncClick = { deviceStatusViewModel.refresh() }
            )
        },
        bottomBar = {
            PillPalBottomNavBar(
                selectedRoute = currentRoute,
                onTabSelected = { route ->
                    hubNavController.navigate(route) {
                        popUpTo(hubNavController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = hubNavController,
            startDestination = Routes.TAB_HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.TAB_HOME) {
                val viewModel: HomeViewModel = hiltViewModel()
                HomeDashboard(viewModel)
            }
            composable(Routes.TAB_SCHEDULE) {
                val viewModel: ScheduleViewModel = hiltViewModel()
                ScheduleHub(viewModel)
            }
            composable(Routes.TAB_HISTORY) {
                val viewModel: HistoryViewModel = hiltViewModel()
                HistoryView(viewModel)
            }
            composable(Routes.TAB_CONTROLS) {
                val viewModel: ControlsViewModel = hiltViewModel()
                ControlsView(viewModel, rootNavController)
            }
        }
    }
}
