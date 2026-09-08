package com.mediwise.presentation.screens.appointment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediwise.presentation.components.EmptyStateCard
import com.mediwise.presentation.theme.*

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediwise.domain.model.Appointment

enum class AppointmentTab { Upcoming, Past, Cancelled }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentListScreen(
    onAppointmentClick: (String) -> Unit,
    onJoinClick: (String) -> Unit = {},
    refreshTick: Int = 0,
    viewModel: AppointmentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTab = AppointmentTab.entries.firstOrNull { it.name == uiState.selectedStatus } ?: AppointmentTab.Upcoming

    LaunchedEffect(refreshTick) {
        if (refreshTick != 0) viewModel.loadAppointments()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text("My Appointments", fontWeight = FontWeight.Bold, color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWhite)
            )
        },
        containerColor = BackgroundWhite
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Tab row
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = BackgroundWhite,
                contentColor = PrimaryBlue
            ) {
                AppointmentTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.onTabSelected(tab.name) },
                        text = { Text(tab.name, fontWeight = FontWeight.Medium) }
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PrimaryBlue)
                        }
                    }
                } else if (uiState.appointments.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.CalendarToday,
                            title = "No ${selectedTab.name.lowercase()} appointments",
                            subtitle = "Appointments you book will appear here.",
                            modifier = Modifier.padding(top = 80.dp)
                        )
                    }
                } else {
                    items(uiState.appointments, key = { it.id }) { appt ->
                        AppointmentCard(
                            appointment = appt,
                            isMutating = uiState.isCancelling,
                            onClick = { onAppointmentClick(appt.id) },
                            onCancel = { viewModel.cancelAppointment(appt.id) },
                            onJoin = { onJoinClick(appt.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppointmentCard(
    appointment: Appointment,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    onJoin: () -> Unit = {},
    isMutating: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Dr. ${appointment.doctorName}",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(appointment.doctorSpecialty, fontSize = 13.sp, color = PrimaryBlue,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                StatusBadge(appointment.status)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Divider)

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null,
                        tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    Text(appointment.date, fontSize = 13.sp, color = TextSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null,
                        tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    Text(appointment.time, fontSize = 13.sp, color = TextSecondary)
                }
            }

            val canCancel = appointment.status == "CONFIRMED" || appointment.status == "PENDING"
            val canJoin = appointment.status == "CONFIRMED" || appointment.status == "IN_PROGRESS"
            if (canCancel || canJoin) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canCancel) {
                        OutlinedButton(
                            onClick = onCancel,
                            enabled = !isMutating,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", fontSize = 13.sp, color = ErrorRed)
                        }
                    }
                    if (canJoin) {
                        Button(
                            onClick = onJoin,
                            enabled = !isMutating,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Icon(Icons.Default.VideoCall, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Join", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (bgColor, textColor, label) = when (status) {
        "CONFIRMED"   -> Triple(AccentGreen.copy(alpha = 0.1f), AccentGreen, "Confirmed")
        "PENDING"     -> Triple(WarningAmber.copy(alpha = 0.1f), WarningAmber, "Pending")
        "COMPLETED"   -> Triple(PrimaryBlueLight, PrimaryBlue, "Completed")
        "CANCELLED"   -> Triple(ErrorRed.copy(alpha = 0.1f), ErrorRed, "Cancelled")
        "IN_PROGRESS" -> Triple(AIPurple.copy(alpha = 0.1f), AIPurple, "In Progress")
        else          -> Triple(Divider, TextSecondary, status)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bgColor) {
        Text(
            label, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
