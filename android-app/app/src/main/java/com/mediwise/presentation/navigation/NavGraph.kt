package com.mediwise.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.mediwise.presentation.screens.doctor.DoctorScheduleScreen
import com.mediwise.presentation.screens.profile.*
import com.mediwise.presentation.screens.notification.NotificationScreen
import com.mediwise.presentation.screens.chat.ChatScreen
import com.mediwise.presentation.screens.chat.ConversationListScreen
import com.mediwise.presentation.screens.call.CallScreen
import com.mediwise.domain.model.CallDirection
import com.mediwise.domain.model.CallMediaType
import com.mediwise.presentation.screens.payment.PaymentScreen
import com.mediwise.presentation.screens.payment.PaymentSuccessScreen
import com.mediwise.presentation.screens.welcome.WelcomeScreen
import com.mediwise.presentation.screens.ai.AiTriageScreen

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
    object Payment : Screen("payment/{appointmentId}") {
        fun createRoute(appointmentId: String) = "payment/$appointmentId"
    }
    object PaymentSuccess : Screen("payment_success/{paymentId}") {
        fun createRoute(paymentId: String) = "payment_success/$paymentId"
    }
    object Chat : Screen("chat/{roomId}") {
        fun createRoute(roomId: String) = "chat/$roomId"
    }
    object Conversations : Screen("conversations")
    object Call : Screen("call/{roomId}/{otherPartyId}/{otherPartyName}/{mediaType}/{direction}") {
        fun createRoute(roomId: String, otherPartyId: String, otherPartyName: String, mediaType: String, direction: String): String {
            val encodedRoom = java.net.URLEncoder.encode(roomId, "UTF-8")
            val encodedName = java.net.URLEncoder.encode(otherPartyName, "UTF-8")
            return "call/$encodedRoom/$otherPartyId/$encodedName/$mediaType/$direction"
        }
    }
    object Notifications : Screen("notifications")
    object AiTriage : Screen("ai_triage")
    object AiPatientReport : Screen("ai_triage/{patientId}") {
        fun createRoute(patientId: String) = "ai_triage/$patientId"
    }
    object ConsultationHistory : Screen("consultation_history")
    object PatientConsultationHistory : Screen("consultation_history/{patientId}") {
        fun createRoute(patientId: String) = "consultation_history/$patientId"
    }
    object Profile : Screen("profile")
    object EditProfile : Screen("edit_profile")
    object Settings : Screen("settings")
    object DoctorSchedule : Screen("doctor_schedule")
}

/**
 * The persistent, YouTube-style bottom-navigation tab routes. Patients see Home/Doctors/
 * Appointments/Profile; doctors see Home/Schedule/Profile instead (ClinicalBottomBar
 * branches on role) — both DoctorList and DoctorSchedule are included here so the bottom
 * bar stays visible on whichever of the two is this user's actual tab.
 */
private val mainTabRoutes = setOf(
    Screen.Home.route,
    Screen.DoctorList.route,
    Screen.Appointments.route,
    Screen.DoctorSchedule.route,
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
    startDestination: String = Screen.Splash.route,
    deepLinkRoute: String? = null
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val role by hiltViewModel<RoleViewModel>().role.collectAsStateWithLifecycle()

    // App-wide incoming-call listener - a call can arrive on any screen, not just a
    // dedicated call screen (see IncomingCallViewModel). Navigation stays here in the
    // composable rather than in the ViewModel, matching how `role`-driven behavior above
    // is handled.
    val incomingCallViewModel = hiltViewModel<IncomingCallViewModel>()
    val incomingCall by incomingCallViewModel.incomingCall.collectAsStateWithLifecycle()
    LaunchedEffect(incomingCall) {
        incomingCall?.let { call ->
            // "Incoming call" is a generic label here - the app doesn't have the caller's
            // display name available from a bare user id (only chat/appointment screens do,
            // via the appointment's denormalized doctor name); CallScreen shows this as-is.
            navController.navigate(
                Screen.Call.createRoute(
                    roomId = call.roomId,
                    otherPartyId = call.callerId,
                    otherPartyName = if (role == com.mediwise.domain.model.Role.DOCTOR) "Patient" else "Doctor",
                    mediaType = call.mediaType.name,
                    direction = CallDirection.INCOMING.name
                )
            )
            incomingCallViewModel.clearIncomingCall()
        }
    }

    // Reselect ticks: incremented when the user taps the already-active tab so that
    // screen refreshes without navigating and without duplicating the back stack.
    var homeRefreshTick by remember { mutableIntStateOf(0) }
    var doctorsRefreshTick by remember { mutableIntStateOf(0) }
    var appointmentsRefreshTick by remember { mutableIntStateOf(0) }
    var doctorScheduleRefreshTick by remember { mutableIntStateOf(0) }
    var profileRefreshTick by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            if (currentRoute in mainTabRoutes) {
                ClinicalBottomBar(
                    navController = navController,
                    currentRoute = currentRoute ?: Screen.Home.route,
                    role = role,
                    onTabReselected = { route ->
                        when (route) {
                            Screen.Home.route -> homeRefreshTick++
                            Screen.DoctorList.route -> doctorsRefreshTick++
                            Screen.Appointments.route -> appointmentsRefreshTick++
                            Screen.DoctorSchedule.route -> doctorScheduleRefreshTick++
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
                SplashScreen(navController, deepLinkRoute = deepLinkRoute)
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

            composable(Screen.AiTriage.route) {
                AiTriageScreen(
                    onBackClick = { navController.navigateUp() },
                    onDoctorClick = { id -> navController.navigate(Screen.DoctorDetail.createRoute(id)) }
                )
            }

            composable(Screen.AiPatientReport.route, arguments = listOf(navArgument("patientId") { type = NavType.StringType })) { backStackEntry ->
                AiTriageScreen(
                    patientId = backStackEntry.arguments?.getString("patientId"),
                    onBackClick = { navController.navigateUp() },
                    onDoctorClick = { id -> navController.navigate(Screen.DoctorDetail.createRoute(id)) }
                )
            }

            composable(Screen.ConsultationHistory.route) {
                ConsultationHistoryScreen(onBackClick = { navController.navigateUp() })
            }

            composable(Screen.PatientConsultationHistory.route, arguments = listOf(navArgument("patientId") { type = NavType.StringType })) { backStackEntry ->
                ConsultationHistoryScreen(
                    patientId = backStackEntry.arguments?.getString("patientId"),
                    onBackClick = { navController.navigateUp() }
                )
            }

            composable(Screen.DoctorList.route) {
                DoctorListScreen(
                    onDoctorClick = { id -> navController.navigate(Screen.DoctorDetail.createRoute(id)) },
                    onFavoritesClick = { navController.navigate(Screen.Favorites.route) },
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
                    onAppointmentBooked = { appointmentId ->
                        navController.navigate(Screen.Payment.createRoute(appointmentId)) {
                            popUpTo(Screen.Schedule.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Payment.route, arguments = listOf(navArgument("appointmentId") { type = NavType.StringType })) { backStackEntry ->
                val appointmentId = backStackEntry.arguments?.getString("appointmentId") ?: ""
                PaymentScreen(
                    appointmentId = appointmentId,
                    onPaymentSuccess = { paymentId ->
                        navController.navigate(Screen.PaymentSuccess.createRoute(paymentId)) {
                            popUpTo(Screen.Home.route)
                        }
                    },
                    onBackClick = {
                        navController.navigate(Screen.Appointments.route) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            composable(Screen.PaymentSuccess.route, arguments = listOf(navArgument("paymentId") { type = NavType.StringType })) { backStackEntry ->
                val paymentId = backStackEntry.arguments?.getString("paymentId") ?: ""
                PaymentSuccessScreen(navController = navController, paymentId = paymentId)
            }

            composable(Screen.Appointments.route) {
                AppointmentListScreen(
                    onAppointmentClick = { id -> navController.navigate(Screen.AppointmentDetail.createRoute(id)) },
                    onJoinClick = { id -> navController.navigate(Screen.Chat.createRoute("appointment_$id")) },
                    refreshTick = appointmentsRefreshTick
                )
            }

            composable(Screen.AppointmentDetail.route, arguments = listOf(navArgument("appointmentId") { type = NavType.StringType })) { backStackEntry ->
                val appointmentId = backStackEntry.arguments?.getString("appointmentId") ?: ""
                AppointmentDetailScreen(
                    appointmentId = appointmentId,
                    onBackClick = { navController.navigateUp() },
                    onJoinClick = { navController.navigate(Screen.Chat.createRoute("appointment_$appointmentId")) },
                    onAiReportClick = { patientId -> navController.navigate(Screen.AiPatientReport.createRoute(patientId)) },
                    onPatientHistoryClick = { patientId -> navController.navigate(Screen.PatientConsultationHistory.createRoute(patientId)) },
                    onReviewClick = { id -> },
                    onAudioCallClick = { otherPartyId ->
                        navController.navigate(
                            Screen.Call.createRoute(
                                roomId = "appointment_$appointmentId",
                                otherPartyId = otherPartyId,
                                otherPartyName = if (role == com.mediwise.domain.model.Role.DOCTOR) "Patient" else "Doctor",
                                mediaType = CallMediaType.AUDIO.name,
                                direction = CallDirection.OUTGOING.name
                            )
                        )
                    },
                    onVideoCallClick = { otherPartyId ->
                        navController.navigate(
                            Screen.Call.createRoute(
                                roomId = "appointment_$appointmentId",
                                otherPartyId = otherPartyId,
                                otherPartyName = if (role == com.mediwise.domain.model.Role.DOCTOR) "Patient" else "Doctor",
                                mediaType = CallMediaType.VIDEO.name,
                                direction = CallDirection.OUTGOING.name
                            )
                        )
                    }
                )
            }

            composable(
                Screen.Call.route,
                arguments = listOf(
                    navArgument("roomId") { type = NavType.StringType },
                    navArgument("otherPartyId") { type = NavType.StringType },
                    navArgument("otherPartyName") { type = NavType.StringType },
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("direction") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val args = backStackEntry.arguments
                val roomId = java.net.URLDecoder.decode(args?.getString("roomId") ?: "", "UTF-8")
                val otherPartyId = args?.getString("otherPartyId") ?: ""
                val otherPartyName = java.net.URLDecoder.decode(args?.getString("otherPartyName") ?: "", "UTF-8")
                val mediaType = CallMediaType.entries.firstOrNull { it.name == args?.getString("mediaType") } ?: CallMediaType.AUDIO
                val direction = CallDirection.entries.firstOrNull { it.name == args?.getString("direction") } ?: CallDirection.OUTGOING

                CallScreen(
                    roomId = roomId,
                    otherPartyId = otherPartyId,
                    otherPartyName = otherPartyName,
                    mediaType = mediaType,
                    direction = direction,
                    onCallEnded = { navController.popBackStack() }
                )
            }

            composable(Screen.Chat.route, arguments = listOf(navArgument("roomId") { type = NavType.StringType })) { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                ChatScreen(
                    navController = navController,
                    roomId = roomId,
                    onAudioCallClick = { otherPartyId, otherPartyName ->
                        navController.navigate(
                            Screen.Call.createRoute(
                                roomId = roomId,
                                otherPartyId = otherPartyId,
                                otherPartyName = otherPartyName,
                                mediaType = CallMediaType.AUDIO.name,
                                direction = CallDirection.OUTGOING.name
                            )
                        )
                    },
                    onVideoCallClick = { otherPartyId, otherPartyName ->
                        navController.navigate(
                            Screen.Call.createRoute(
                                roomId = roomId,
                                otherPartyId = otherPartyId,
                                otherPartyName = otherPartyName,
                                mediaType = CallMediaType.VIDEO.name,
                                direction = CallDirection.OUTGOING.name
                            )
                        )
                    }
                )
            }

            composable(Screen.Conversations.route) {
                ConversationListScreen(
                    onConversationClick = { appointmentId ->
                        navController.navigate(Screen.Chat.createRoute("appointment_$appointmentId"))
                    },
                    onBackClick = { navController.navigateUp() }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onEditClick = { navController.navigate(Screen.EditProfile.route) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                    onConsultationHistoryClick = { navController.navigate(Screen.ConsultationHistory.route) },
                    onLogoutClick = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    refreshTick = profileRefreshTick
                )
            }

            composable(Screen.DoctorSchedule.route) {
                // Reached only via the doctor's bottom-nav "Schedule" tab — there is no
                // other entry point, so there's nothing to navigate back to.
                DoctorScheduleScreen(
                    onBackClick = { navController.navigateUp() },
                    showBackButton = false,
                    refreshTick = doctorScheduleRefreshTick
                )
            }

            composable(Screen.EditProfile.route) {
                EditProfileScreen(
                    // ProfileScreen and EditProfileScreen are separate nav destinations, so Hilt
                    // gives each its own ProfileViewModel instance — neither a save nor an avatar
                    // upload here touches ProfileScreen's already-loaded state. Bumping the tick
                    // on the way out (back OR save — an avatar upload persists immediately, before
                    // Save is ever pressed) forces ProfileScreen's LaunchedEffect to reload.
                    onBackClick = {
                        profileRefreshTick++
                        navController.navigateUp()
                    },
                    onSaveClick = {
                        profileRefreshTick++
                        navController.navigateUp()
                    }
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
