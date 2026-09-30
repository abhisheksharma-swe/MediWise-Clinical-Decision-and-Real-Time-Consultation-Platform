package com.mediwise.presentation.screens.doctors

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediwise.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDetailScreen(
    doctorId: String,
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    viewModel: DoctorDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val doctor = uiState.doctor
    val context = LocalContext.current

    val initials = remember(doctor?.fullName) {
        doctor?.fullName?.split(" ")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { it.firstOrNull()?.toString() }
            ?.take(2)
            ?.joinToString("")
            ?.uppercase() ?: "DR"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (uiState.isFavorite) ErrorRed else Color.White
                        )
                    }
                    IconButton(onClick = {
                        doctor?.let { doc ->
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Consult with Dr. ${doc.fullName} (${doc.specialty}) on MediWise! Consultation Fee: ₹${doc.consultationFee}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Doctor Profile"))
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            if (doctor != null) {
                Surface(
                    shadowElevation = 8.dp,
                    color = SurfaceWhite
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onBookClick(doctorId) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder
                        ) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = PrimaryBlue)
                            Spacer(Modifier.width(8.dp))
                            Text("Chat / Query", color = PrimaryBlue)
                        }
                        Button(
                            onClick = { onBookClick(doctorId) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Book Slot", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(uiState.error ?: "Failed to load doctor", fontSize = 16.sp, color = TextPrimary)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadDoctor() },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
            doctor != null -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Hero section
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Brush.verticalGradient(listOf(PrimaryBlue, PrimaryBlueDark)))
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        initials, color = Color.White,
                                        fontWeight = FontWeight.Bold, fontSize = 26.sp
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Dr. ${doctor.fullName}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.Verified,
                                        contentDescription = "Verified",
                                        tint = Color(0xFF4FC3F7),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    doctor.specialty,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // Stats row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatCard(
                                value = if (doctor.avgRating > 0) String.format("%.1f", doctor.avgRating) else "${doctor.rating}",
                                label = "Rating",
                                icon = Icons.Default.Star,
                                color = WarningAmber,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                value = "${doctor.totalReviews.takeIf { it > 0 } ?: doctor.reviewCount}+",
                                label = "Reviews",
                                icon = Icons.Default.People,
                                color = PrimaryBlue,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                value = "${doctor.experienceYears}y",
                                label = "Experience",
                                icon = Icons.Default.WorkHistory,
                                color = AccentGreen,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                value = "₹${doctor.consultationFee}",
                                label = "Fee",
                                icon = Icons.Default.Payments,
                                color = AIPurple,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // About section
                    item {
                        SectionCard(title = "About Doctor") {
                            Text(
                                text = doctor.bio.ifBlank {
                                    "Senior specialist in ${doctor.specialty} with over ${doctor.experienceYears} years of clinical experience dedicated to providing patient-centered care and modern diagnostic treatments."
                                },
                                fontSize = 14.sp,
                                color = TextSecondary,
                                lineHeight = 22.sp
                            )
                        }
                    }

                    // Available Today section
                    item {
                        SectionCard(title = "Available Today") {
                            if (uiState.todaySlots.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(uiState.todaySlots, key = { it.id }) { slot ->
                                        SlotChip(time = slot.startTime, onClick = { onBookClick(doctorId) })
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "No open slots for today",
                                        fontSize = 13.sp,
                                        color = TextSecondary
                                    )
                                    TextButton(onClick = { onBookClick(doctorId) }) {
                                        Text("Pick another date →", color = PrimaryBlue, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Reviews section
                    item {
                        SectionCard(title = "Patient Reviews (${doctor.totalReviews.takeIf { it > 0 } ?: doctor.reviewCount})") {
                            if (uiState.reviews.isEmpty()) {
                                Text(
                                    "No reviews yet",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            } else {
                                uiState.reviews.forEachIndexed { index, review ->
                                    ReviewItem(
                                        name = review.patientName.ifBlank { "Patient" },
                                        rating = review.rating,
                                        comment = review.reviewText
                                    )
                                    if (index < uiState.reviews.size - 1) {
                                        HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 10.dp))
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
            Text(label, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SlotChip(time: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = PrimaryBlueLight,
        onClick = onClick
    ) {
        Text(
            time,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = PrimaryBlue,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ReviewItem(name: String, rating: Int, comment: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PrimaryBlueLight),
            contentAlignment = Alignment.Center
        ) {
            Text(name.first().toString(), color = PrimaryBlue, fontWeight = FontWeight.Bold)
        }
        Column {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                Row {
                    repeat(rating) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(comment, fontSize = 13.sp, color = TextSecondary, lineHeight = 20.sp)
        }
    }
}

