package com.mediwise.presentation.screens.doctor

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediwise.domain.model.Appointment
import com.mediwise.presentation.components.EmptyStateCard
import com.mediwise.presentation.screens.appointment.StatusBadge
import com.mediwise.presentation.theme.*

enum class DoctorScheduleTab { Upcoming, Completed, Cancelled }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorScheduleScreen(
    onBackClick: () -> Unit,
    /** False when reached as the bottom-nav tab root for doctors — there's nothing to go back to. */
    showBackButton: Boolean = true,
    refreshTick: Int = 0,
    viewModel: DoctorScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTab = DoctorScheduleTab.entries.firstOrNull { it.name == uiState.selectedStatus } ?: DoctorScheduleTab.Upcoming
    var completingAppointmentId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshTick) {
        if (refreshTick != 0) viewModel.loadAppointments()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Schedule", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWhite)
            )
        },
        containerColor = BackgroundWhite
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = BackgroundWhite,
                contentColor = PrimaryBlue
            ) {
                DoctorScheduleTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.onTabSelected(tab.name) },
                        text = { Text(tab.name, fontWeight = FontWeight.Medium) }
                    )
                }
            }

            uiState.error?.let { error ->
                Surface(
                    color = ErrorRedLight,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        error,
                        color = ErrorRed,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
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
                            subtitle = "Your patient appointments will appear here.",
                            modifier = Modifier.padding(top = 80.dp)
                        )
                    }
                } else {
                    items(uiState.appointments, key = { it.id }) { appt ->
                        DoctorAppointmentCard(
                            appointment = appt,
                            isUpdating = uiState.isUpdating,
                            onStart = { viewModel.startAppointment(appt.id) },
                            onCompleteClick = { completingAppointmentId = appt.id }
                        )
                    }
                }
            }
        }
    }

    completingAppointmentId?.let { appointmentId ->
        CompleteConsultationDialog(
            isSubmitting = uiState.isUpdating,
            onDismiss = { completingAppointmentId = null },
            onSubmit = { notes, diagnosis, prescription ->
                viewModel.completeAppointment(appointmentId, notes, diagnosis, prescription)
                completingAppointmentId = null
            }
        )
    }
}

@Composable
private fun DoctorAppointmentCard(
    appointment: Appointment,
    isUpdating: Boolean,
    onStart: () -> Unit,
    onCompleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    Text("Patient ID: ${appointment.patientId.take(8)}",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (appointment.chiefComplaint.isNotBlank()) {
                        Text(appointment.chiefComplaint, fontSize = 13.sp, color = TextSecondary,
                            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
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

            if (appointment.status == "CONFIRMED") {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onStart,
                    enabled = !isUpdating,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Start Consultation", fontSize = 13.sp)
                }
            } else if (appointment.status == "IN_PROGRESS") {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onCompleteClick,
                    enabled = !isUpdating,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Complete Consultation", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun CompleteConsultationDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (notes: String, diagnosis: String?, prescription: String?) -> Unit
) {
    var notes by remember { mutableStateOf("") }
    var diagnosis by remember { mutableStateOf("") }
    var prescription by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Complete Consultation", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Clinical notes *") },
                    placeholder = { Text("Required", color = TextSecondary) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Divider,
                        focusedLabelColor = PrimaryBlue
                    ),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = diagnosis,
                    onValueChange = { diagnosis = it },
                    label = { Text("Diagnosis (optional)") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Divider,
                        focusedLabelColor = PrimaryBlue
                    ),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prescription,
                    onValueChange = { prescription = it },
                    label = { Text("Prescription (optional)") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Divider,
                        focusedLabelColor = PrimaryBlue
                    ),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(notes.trim(), diagnosis.trim().ifBlank { null }, prescription.trim().ifBlank { null })
                },
                enabled = notes.isNotBlank() && !isSubmitting
            ) {
                Text("Submit", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
