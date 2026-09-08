package com.mediwise.presentation.screens.appointment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediwise.presentation.components.ErrorStateCard
import com.mediwise.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentDetailScreen(
    appointmentId: String,
    onBackClick: () -> Unit,
    onJoinClick: () -> Unit,
    onAiReportClick: (String) -> Unit,
    onPatientHistoryClick: (String) -> Unit = {},
    onReviewClick: (String) -> Unit,
    onAudioCallClick: (otherPartyUserId: String) -> Unit = {},
    onVideoCallClick: (otherPartyUserId: String) -> Unit = {},
    viewModel: AppointmentDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCancelConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(appointmentId) {
        viewModel.load(appointmentId)
    }

    LaunchedEffect(uiState.cancelled) {
        if (uiState.cancelled) onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appointment Details", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWhite)
            )
        },
        containerColor = BackgroundWhite
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            }
            uiState.error != null && uiState.appointment == null -> {
                Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
                    ErrorStateCard(
                        message = uiState.error ?: "Failed to load appointment",
                        onRetry = { viewModel.load(appointmentId) }
                    )
                }
            }
            uiState.appointment != null -> {
                val appt = uiState.appointment!!
                val canCancel = appt.status == "CONFIRMED" || appt.status == "PENDING"
                val canJoin = appt.status == "CONFIRMED" || appt.status == "IN_PROGRESS"
                val canReview = appt.status == "COMPLETED"

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    item {
                        val (bgColor, iconColor, label) = when (appt.status) {
                            "CONFIRMED" -> Triple(AccentGreen.copy(alpha = 0.08f), AccentGreen, "Appointment Confirmed")
                            "PENDING" -> Triple(WarningAmber.copy(alpha = 0.08f), WarningAmber, "Awaiting Confirmation")
                            "COMPLETED" -> Triple(PrimaryBlueLight, PrimaryBlue, "Consultation Completed")
                            "CANCELLED" -> Triple(ErrorRed.copy(alpha = 0.08f), ErrorRed, "Appointment Cancelled")
                            "IN_PROGRESS" -> Triple(AIPurple.copy(alpha = 0.08f), AIPurple, "Consultation In Progress")
                            "NO_SHOW" -> Triple(ErrorRed.copy(alpha = 0.08f), ErrorRed, "Marked as No-Show")
                            else -> Triple(Divider, TextSecondary, appt.status)
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = bgColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = iconColor, modifier = Modifier.size(36.dp))
                                Column {
                                    Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                                    Text("ID: #${appt.id.take(8).uppercase()}", fontSize = 13.sp, color = TextSecondary)
                                }
                            }
                        }
                    }

                    item {
                        InfoCard(title = "Doctor") {
                            DetailRow(Icons.Default.Person, "Name", "Dr. ${appt.doctorName.ifBlank { "—" }}")
                            DetailRow(Icons.Default.MedicalServices, "Specialty", appt.doctorSpecialty.ifBlank { "—" })
                        }
                    }

                    item {
                        InfoCard(title = "Appointment Info") {
                            DetailRow(Icons.Default.CalendarMonth, "Date", appt.date.ifBlank { "—" })
                            DetailRow(Icons.Default.Schedule, "Time", appt.time.ifBlank { "—" })
                            DetailRow(Icons.Default.VideoCall, "Type", appt.type)
                        }
                    }

                    if (appt.chiefComplaint.isNotBlank()) {
                        item {
                            InfoCard(title = "Chief Complaint") {
                                Text(appt.chiefComplaint, fontSize = 14.sp, color = TextPrimary)
                            }
                        }
                    }

                    if (canJoin) {
                        item {
                            OutlinedButton(
                                onClick = { onPatientHistoryClick(appt.patientId) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("View patient consultation history")
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = { onAiReportClick(appt.patientId) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("View AI symptom report")
                        }
                    }

                    if (canJoin) {
                        item {
                            Button(
                                onClick = onJoinClick,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Message", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Calling needs the other party's user id (not just their profile id)
                        // to address WebRTC signaling - only offered once the backend has
                        // actually supplied it (see AppointmentDetailViewModel.load).
                        if (uiState.otherPartyUserId.isNotBlank()) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onAudioCallClick(uiState.otherPartyUserId) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Audio Call", fontSize = 13.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { onVideoCallClick(uiState.otherPartyUserId) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.VideoCall, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Video Call", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (canCancel || canReview) {
                        item {
                            if (uiState.error != null) {
                                Text(uiState.error ?: "", color = ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (canReview) {
                                    OutlinedButton(
                                        onClick = { onReviewClick(appointmentId) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.StarBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Review", fontSize = 13.sp)
                                    }
                                }
                                if (canCancel) {
                                    OutlinedButton(
                                        onClick = { showCancelConfirm = true },
                                        enabled = !uiState.isCancelling,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                                    ) {
                                        if (uiState.isCancelling) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ErrorRed)
                                        } else {
                                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Cancel", fontSize = 13.sp, color = ErrorRed)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }

                if (showCancelConfirm) {
                    AlertDialog(
                        onDismissRequest = { showCancelConfirm = false },
                        title = { Text("Cancel Appointment?") },
                        text = { Text("This cannot be undone. Are you sure you want to cancel this appointment?") },
                        confirmButton = {
                            TextButton(onClick = {
                                showCancelConfirm = false
                                viewModel.cancel(appointmentId)
                            }) { Text("Yes, Cancel", color = ErrorRed) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCancelConfirm = false }) { Text("Keep It") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
        Column {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(value, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
    }
}
