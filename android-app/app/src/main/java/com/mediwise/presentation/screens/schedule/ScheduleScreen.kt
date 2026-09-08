package com.mediwise.presentation.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediwise.presentation.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    doctorId: String,
    onBackClick: () -> Unit,
    onAppointmentBooked: (appointmentId: String) -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(selectedDate, doctorId) {
        viewModel.loadSlotsForDate(selectedDate, doctorId)
    }

    LaunchedEffect(doctorId) {
        viewModel.loadDoctorName(doctorId)
    }

    var selectedSlotModel by remember { mutableStateOf<com.mediwise.domain.model.SlotModel?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Book Appointment", fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, color = TextPrimary)
                        Text(
                            uiState.doctorName.ifBlank { null }?.let { "Dr. $it" } ?: "Loading...",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWhite)
            )
        },
        bottomBar = {
            if (selectedSlotModel != null) {
                Surface(shadowElevation = 8.dp, color = SurfaceWhite) {
                    Column {
                        if (uiState.error != null) {
                            Text(
                                uiState.error ?: "",
                                color = ErrorRed,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        Button(
                            onClick = {
                                selectedSlotModel?.let { slot ->
                                    viewModel.lockAndBookSlot(slot.id, doctorId) { appointmentId ->
                                        onAppointmentBooked(appointmentId)
                                    }
                                }
                            },
                            enabled = !uiState.isLocking && !uiState.isBooking,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            if (uiState.isLocking || uiState.isBooking) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Confirm: ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))} at ${selectedSlotModel?.startTime}",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = BackgroundWhite
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Month navigation
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Month header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            currentMonth = currentMonth.minusMonths(1)
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = PrimaryBlue)
                        }
                        Text(
                            currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary
                        )
                        IconButton(onClick = {
                            currentMonth = currentMonth.plusMonths(1)
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = PrimaryBlue)
                        }
                    }

                    // Day-of-week header
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                            Text(
                                day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))

                    // Calendar grid
                    val firstDay = currentMonth.atDay(1).dayOfWeek.value % 7
                    val daysInMonth = currentMonth.lengthOfMonth()
                    val cells = (0 until firstDay).map { null } +
                            (1..daysInMonth).map { currentMonth.atDay(it) }
                    val padded = cells + (0 until (7 - cells.size % 7) % 7).map { null }

                    padded.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                val isToday = date == LocalDate.now()
                                val isSelected = date == selectedDate
                                val isPast = date != null && date.isBefore(LocalDate.now())

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isSelected -> PrimaryBlue
                                                isToday -> PrimaryBlueLight
                                                else -> Color.Transparent
                                            }
                                        )
                                        .then(
                                            if (date != null && !isPast)
                                                Modifier.clickable { 
                                                    selectedDate = date 
                                                    selectedSlotModel = null
                                                }
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (date != null) {
                                        Text(
                                            "${date.dayOfMonth}",
                                            fontSize = 13.sp,
                                            color = when {
                                                isSelected -> Color.White
                                                isPast -> TextSecondary.copy(alpha = 0.3f)
                                                isToday -> PrimaryBlue
                                                else -> TextPrimary
                                            },
                                            fontWeight = if (isSelected || isToday)
                                                FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Slot picker
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Available Slots — ${selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary
                    )
                    Spacer(Modifier.height(12.dp))

                    if (uiState.isLoading) {
                        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PrimaryBlue)
                        }
                    } else if (uiState.slots.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            Text("No slots available for this date", color = TextSecondary, fontSize = 13.sp)
                        }
                    } else {
                        // Legend
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LegendItem(color = PrimaryBlue, label = "Selected")
                            LegendItem(color = PrimaryBlueLight, label = "Available")
                            LegendItem(color = Divider, label = "Booked")
                        }
                        Spacer(Modifier.height(12.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(200.dp)
                        ) {
                            items(uiState.slots, key = { it.id }) { slot ->
                                val isBooked = slot.status != "AVAILABLE"
                                val isSelected = selectedSlotModel?.id == slot.id

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = when {
                                        isSelected -> PrimaryBlue
                                        isBooked -> Divider
                                        else -> PrimaryBlueLight
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (!isBooked) Modifier.clickable { selectedSlotModel = slot }
                                            else Modifier
                                        )
                                ) {
                                    Text(
                                        slot.startTime,
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        textAlign = TextAlign.Center,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = when {
                                            isSelected -> Color.White
                                            isBooked -> TextSecondary.copy(alpha = 0.5f)
                                            else -> PrimaryBlue
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun AnimatedVisibility(visible: Boolean, content: @Composable () -> Unit) {
    if (visible) content()
}
