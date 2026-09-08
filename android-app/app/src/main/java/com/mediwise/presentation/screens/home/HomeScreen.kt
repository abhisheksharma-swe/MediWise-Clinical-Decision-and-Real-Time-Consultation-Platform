package com.mediwise.presentation.screens.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mediwise.presentation.components.*
import com.mediwise.presentation.navigation.Screen
import com.mediwise.presentation.navigation.navigateToMainTab
import com.mediwise.presentation.theme.*

private val mainTabRoutes = setOf(
    Screen.Home.route, Screen.DoctorList.route, Screen.Appointments.route,
    Screen.DoctorSchedule.route, Screen.Profile.route
)

private fun NavController.navigateFromHome(route: String) {
    if (route in mainTabRoutes) navigateToMainTab(route) else navigate(route)
}

private val joinableStatuses = setOf("CONFIRMED", "IN_PROGRESS")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    refreshTick: Int = 0,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(refreshTick) {
        if (refreshTick != 0) viewModel.refresh()
    }

    // Request POST_NOTIFICATIONS at a natural point post-login on Android 13+, matching
    // the same "ask via an activity-result launcher" style used for image picking elsewhere.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: FCM display already checks the live permission before posting */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Good Afternoon!", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(uiState.userName.ifBlank { "User" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                        BadgedBox(badge = {
                            if (uiState.unreadNotifications > 0)
                                Badge { Text(uiState.unreadNotifications.toString()) }
                        }) {
                            Icon(Icons.Default.Notifications, "Notifications")
                        }
                    }
                    IconButton(onClick = { navController.navigateFromHome(Screen.Profile.route) }) {
                        Icon(Icons.Default.Person, "Profile")
                    }
                }
            )
        }
    ) { padding ->
        val hasContent = uiState.userName != "User" || uiState.topDoctors.isNotEmpty() || uiState.upcomingAppointments.isNotEmpty()

        if (uiState.isLoading && !hasContent) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            state = pullToRefreshState,
            modifier = Modifier.padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                uiState.error?.let { error ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(ErrorRedLight)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(error, color = ErrorRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.refresh() }) {
                                Text("Retry", color = ErrorRed, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // ── AI Health Summary Banner — self-service symptom triage is a
                // patient-only concept (a doctor has no "own symptoms" to submit; the
                // backend's AiService.assertSymptomLogAccess only allows a PATIENT-role
                // user through this self-entry path and 401s anyone else). ──
                if (!uiState.isDoctor) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clip(MaterialTheme.shapes.large)
                                .clickable { navController.navigate(Screen.AiTriage.route) }
                                .background(
                                    Brush.horizontalGradient(listOf(PrimaryBlue, PrimaryBlueDark))
                                )
                                .padding(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Your Health Score", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                                    Text("Check your symptoms", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.2f), MaterialTheme.shapes.small)
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("AI-Powered Insights", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Icon(Icons.Default.LocalHospital, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                }

//                // ── Quick Actions ──────────────────────────────────────────────
//                item {
//                    Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
//                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
//                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(quickActions) { action ->
                            QuickActionCard(action) {
                                navController.navigateFromHome(action.route)
                            }
                        }
                    }
                }

                // ── Upcoming Appointments ─────────────────────────────────────
                item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (uiState.isDoctor) "Upcoming Consultations" else "Upcoming Appointments",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            val route = if (uiState.isDoctor) Screen.DoctorSchedule.route else Screen.Appointments.route
                            navController.navigateFromHome(route)
                        }) {
                            Text("See all", color = PrimaryBlue)
                        }
                    }
                }

                if (uiState.upcomingAppointments.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.CalendarToday,
                            title = if (uiState.isDoctor) "No upcoming consultations" else "No upcoming appointments",
                            subtitle = if (uiState.isDoctor) "New bookings from patients will appear here" else "Book a consultation with a doctor",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(uiState.upcomingAppointments.take(3)) { appt ->
                        AppointmentSummaryCard(
                            appt = appt,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            onJoinClick = if (appt.status in joinableStatuses) {
                                { navController.navigate(Screen.Chat.createRoute("appointment_${appt.id}")) }
                            } else null
                        )
                    }
                }

                // ── Top Doctors — a patient-only concept; doctors don't browse peers here. ──
                if (!uiState.isDoctor) {
                    item {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Top Doctors", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { navController.navigateFromHome(Screen.DoctorList.route) }) {
                                Text("See all", color = PrimaryBlue)
                            }
                        }
                    }

                    items(uiState.topDoctors.take(3)) { doctor ->
                        DoctorCard(
                            doctor = doctor,
                            onClick = { navController.navigate(Screen.DoctorDetail.createRoute(doctor.id)) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private val quickActions = listOf(
//    QuickActionItem(Icons.Default.AutoAwesome, "AI Triage", Screen.AiTriage.route),
    QuickActionItem(Icons.Default.LocalHospital, "Find Doctor", Screen.DoctorList.route),
    QuickActionItem(Icons.AutoMirrored.Filled.EventNote, "My Bookings", Screen.Appointments.route),
    QuickActionItem(Icons.AutoMirrored.Filled.Chat, "Messages", Screen.Conversations.route),
    QuickActionItem(Icons.AutoMirrored.Filled.ReceiptLong, "Prescriptions", Screen.Profile.route)
)
