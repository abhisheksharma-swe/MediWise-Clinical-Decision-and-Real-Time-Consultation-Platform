package com.mediwise.presentation.screens.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mediwise.presentation.components.*
import com.mediwise.presentation.navigation.Screen
import com.mediwise.presentation.navigation.navigateToMainTab
import com.mediwise.presentation.theme.*

private val mainTabRoutes = setOf(
    Screen.Home.route, Screen.DoctorList.route, Screen.Appointments.route, Screen.Profile.route
)

private fun NavController.navigateFromHome(route: String) {
    if (route in mainTabRoutes) navigateToMainTab(route) else navigate(route)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    refreshTick: Int = 0,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(refreshTick) {
        if (refreshTick != 0) viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Good morning! 👋", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
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
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── AI Health Summary Banner ──────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            Brush.horizontalGradient(listOf(PrimaryBlue, PrimaryBlueDark))
                        )
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Your Health Score", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                            Text("AI analysis coming soon", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                        Text("🏥", style = MaterialTheme.typography.displayMedium)
                    }
                }
            }

            // ── Quick Actions ──────────────────────────────────────────────
            item {
                Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

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
                    Text("Upcoming Appointments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { navController.navigateFromHome(Screen.Appointments.route) }) {
                        Text("See all", color = PrimaryBlue)
                    }
                }
            }

            if (uiState.upcomingAppointments.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = "📅",
                        title = "No upcoming appointments",
                        subtitle = "Book a consultation with a doctor",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(uiState.upcomingAppointments.take(3)) { appt ->
                    AppointmentSummaryCard(appt, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }

            // ── Top Doctors ───────────────────────────────────────────────
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

private data class QuickAction(val icon: String, val label: String, val route: String)

private val quickActions = listOf(
    QuickAction("👩‍⚕️", "Find Doctor", Screen.DoctorList.route),
    QuickAction("📅", "My Bookings", Screen.Appointments.route),
    QuickAction("💬", "Chat", Screen.Notifications.route),
    QuickAction("💊", "Prescriptions", Screen.Profile.route)
)
