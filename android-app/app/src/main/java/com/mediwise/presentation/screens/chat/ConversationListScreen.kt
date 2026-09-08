package com.mediwise.presentation.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediwise.domain.model.Appointment
import com.mediwise.presentation.components.EmptyStateCard
import com.mediwise.presentation.components.ErrorStateCard
import com.mediwise.presentation.screens.appointment.StatusBadge
import com.mediwise.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (appointmentId: String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: ConversationListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWhite)
            )
        },
        containerColor = BackgroundWhite
    ) { padding ->
        when {
            uiState.isLoading && uiState.conversations.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            }

            uiState.error != null && uiState.conversations.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
                    ErrorStateCard(
                        message = uiState.error ?: "Failed to load conversations",
                        onRetry = { viewModel.load() }
                    )
                }
            }

            uiState.conversations.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        title = "No conversations yet",
                        subtitle = if (uiState.isDoctor)
                            "Chats with confirmed patients will appear here."
                        else
                            "Chats with your confirmed doctors will appear here."
                    )
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    items(uiState.conversations, key = { it.id }) { appt ->
                        ConversationCard(
                            appointment = appt,
                            isDoctor = uiState.isDoctor,
                            onClick = { onConversationClick(appt.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationCard(appointment: Appointment, isDoctor: Boolean, onClick: () -> Unit) {
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
                    // The domain Appointment model only carries the doctor's identity today
                    // (see ChatViewModel.loadOtherPartyName) — a doctor's view of their own
                    // conversation list falls back to a patient identifier, same as
                    // DoctorScheduleScreen's appointment cards.
                    Text(
                        text = if (isDoctor) "Patient ID: ${appointment.patientId.take(8)}" else "Dr. ${appointment.doctorName}",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (!isDoctor && appointment.doctorSpecialty.isNotBlank()) {
                        Text(appointment.doctorSpecialty, fontSize = 13.sp, color = PrimaryBlue,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                StatusBadge(appointment.status)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Divider)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        Text(appointment.date, fontSize = 13.sp, color = TextSecondary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        Text(appointment.time, fontSize = 13.sp, color = TextSecondary)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Open chat", tint = PrimaryBlue)
            }
        }
    }
}
