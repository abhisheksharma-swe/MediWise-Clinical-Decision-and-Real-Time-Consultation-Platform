package com.mediwise.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mediwise.presentation.components.ClinicalBottomBar
import com.mediwise.presentation.screens.auth.*
import com.mediwise.presentation.screens.splash.SplashScreen
import com.mediwise.presentation.screens.home.HomeScreen
import com.mediwise.presentation.screens.doctors.*
import com.mediwise.presentation.screens.schedule.ScheduleScreen
import com.mediwise.presentation.screens.appointment.*
import com.mediwise.presentation.screens.profile.*
import com.mediwise.presentation.screens.notification.NotificationScreen
import com.mediwise.presentation.screens.chat.ChatScreen
import com.mediwise.presentation.screens.welcome.WelcomeScreen

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Welcome : Screen("welcome")
    object Home : Screen("home")
    object Login : Screen("login")
    object Signup : Screen("signup")
    object ResetPassword : Screen("reset_password")
    object OtpVerify : Screen("otp_verify/{phone}/{mode}") {
        fun createRoute(phone: String, mode: String) = "otp_verify/$phone/$mode"
    }
    object DoctorList : Screen("doctors")
    object DoctorDetail : Screen("doctors/{doctorId}") {
        fun createRoute(id: String) = "doctors/$id"
    }
    object Favorites : Screen("favorites")
    object Schedule : Screen("schedule/{doctorId}") {
        fun createRoute(id: String) = "schedule/$id"
    }
    object Appointments : Screen("appointments")
    object AppointmentDetail : Screen("appointments/{appointmentId}") {
        fun createRoute(id: String) = "appointments/$id"
    }
    object Chat : Screen("chat/{roomId}") {
        fun createRoute(roomId: String) = "chat/$roomId"
    }
    object Notifications : Screen("notifications")
    object Profile : Screen("profile")
    object EditProfile : Screen("edit_profile")
    object Settings : Screen("settings")
}

/** The four persistent, YouTube-style bottom-navigation tabs. */
private val mainTabRoutes = setOf(
    Screen.Home.route,
    Screen.DoctorList.route,
    Screen.Appointments.route,
    Screen.Profile.route
)

/**
 * Navigates to a top-level tab, preserving each tab's saved back-stack/scroll state
 * and avoiding duplicate copies of the destination in the back stack.
 */
fun NavController.navigateToMainTab(route: String) {
    navigate(route) {
        popUpTo(Screen.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // Reselect ticks: incremented when the user taps the already-active tab so that
    // screen refreshes without navigating and without duplicating the back stack.
    var homeRefreshTick by remember { mutableIntStateOf(0) }
    var doctorsRefreshTick by remember { mutableIntStateOf(0) }
    var appointmentsRefreshTick by remember { mutableIntStateOf(0) }
    var profileRefreshTick by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            if (currentRoute in mainTabRoutes) {
                ClinicalBottomBar(
                    navController = navController,
                    currentRoute = currentRoute ?: Screen.Home.route,
                    onTabReselected = { route ->
                        when (route) {
                            Screen.Home.route -> homeRefreshTick++
                            Screen.DoctorList.route -> doctorsRefreshTick++
                            Screen.Appointments.route -> appointmentsRefreshTick++
                            Screen.Profile.route -> profileRefreshTick++
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(navController)
            }

            composable(Screen.Welcome.route) {
                WelcomeScreen(navController, onNavigateToOtp = {})
            }

            composable(Screen.Login.route) {
                LoginScreen(navController)
            }

            composable(Screen.Signup.route) {
                SignupScreen(
                    onNavigateToLogin = { navController.navigateUp() },
                    onNavigateToOtp = { phone -> navController.navigate(Screen.OtpVerify.createRoute(phone, "signup")) }
                )
            }

            composable(
                route = Screen.OtpVerify.route,
                arguments = listOf(
                    navArgument("phone") { type = NavType.StringType },
                    navArgument("mode") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val phone = backStackEntry.arguments?.getString("phone") ?: ""
                val mode = backStackEntry.arguments?.getString("mode") ?: "signup"
                OtpScreen(
                    phone = phone,
                    mode = mode,
                    onVerificationSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onBackClick = { navController.navigateUp() }
                )
            }

            composable(Screen.ResetPassword.route) {
                ResetPasswordScreen(
                    onBackClick = { navController.navigateUp() },
                    onResetSuccess = { navController.navigateUp() }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(navController, refreshTick = homeRefreshTick)
            }

            composable(Screen.DoctorList.route) {
                DoctorListScreen(
                    onDoctorClick = { id -> navController.navigate(Screen.DoctorDetail.createRoute(id)) },
                    refreshTick = doctorsRefreshTick
                )
            }

            composable(Screen.DoctorDetail.route, arguments = listOf(navArgument("doctorId") { type = NavType.StringType })) { backStackEntry ->
                val doctorId = backStackEntry.arguments?.getString("doctorId") ?: ""
                DoctorDetailScreen(
                    doctorId = doctorId,
                    onBackClick = { navController.navigateUp() },
                    onBookClick = { id -> navController.navigate(Screen.Schedule.createRoute(id)) }
                )
            }

            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    onDoctorClick = { id -> navController.navigate(Screen.DoctorDetail.createRoute(id)) },
                    onBackClick = { navController.navigateUp() }
                )
            }

            composable(Screen.Schedule.route, arguments = listOf(navArgument("doctorId") { type = NavType.StringType })) { backStackEntry ->
                val doctorId = backStackEntry.arguments?.getString("doctorId") ?: ""
                ScheduleScreen(
                    doctorId = doctorId,
                    onBackClick = { navController.navigateUp() },
                    onSlotSelected = { date, time ->
                        navController.navigate(Screen.Appointments.route) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            composable(Screen.Appointments.route) {
                AppointmentListScreen(
                    onAppointmentClick = { id -> navController.navigate(Screen.AppointmentDetail.createRoute(id)) },
                    refreshTick = appointmentsRefreshTick
                )
            }

            composable(Screen.AppointmentDetail.route, arguments = listOf(navArgument("appointmentId") { type = NavType.StringType })) { backStackEntry ->
                val appointmentId = backStackEntry.arguments?.getString("appointmentId") ?: ""
                AppointmentDetailScreen(
                    appointmentId = appointmentId,
                    onBackClick = { navController.navigateUp() },
                    onJoinClick = { },
                    onReviewClick = { id -> }
                )
            }

            composable(Screen.Chat.route, arguments = listOf(navArgument("roomId") { type = NavType.StringType })) { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                ChatScreen(
                    navController = navController,
                    roomId = roomId
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onEditClick = { navController.navigate(Screen.EditProfile.route) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                    onLogoutClick = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    refreshTick = profileRefreshTick
                )
            }

            composable(Screen.EditProfile.route) {
                EditProfileScreen(
                    onBackClick = { navController.navigateUp() },
                    onSaveClick = { navController.navigateUp() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBackClick = { navController.navigateUp() }
                )
            }

            composable(Screen.Notifications.route) {
                NotificationScreen(
                    onBackClick = { navController.navigateUp() }
                )
            }
        }
    }
}
