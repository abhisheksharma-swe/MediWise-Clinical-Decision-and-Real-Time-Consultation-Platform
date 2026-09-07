package com.mediwise.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediwise.presentation.theme.*
import com.mediwise.domain.model.Doctor
import com.mediwise.domain.model.Appointment
import com.mediwise.presentation.navigation.navigateToMainTab

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.unit.sp

// ── MediWise Pill Button (Primary) ──────────────────────────────────────────
@Composable
fun MediWisePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = BrandBlue,
    contentColor: Color = Color.White
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.7f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 2.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = contentColor, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
        } else {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── MediWise Secondary Pill Button ──────────────────────────────────────────
@Composable
fun MediWiseSecondaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = BrandBlueLight,
    contentColor: Color = BrandBlue
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 1.dp)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── MediWise Soft Input Field (Matches Mockup) ──────────────────────────────
@Composable
fun MediWiseInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = InputPlaceholder,
                    fontSize = 14.sp
                )
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            isError = isError,
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = InputBackground,
                unfocusedContainerColor = InputBackground,
                disabledContainerColor = InputBackground,
                focusedBorderColor = BrandBlue,
                unfocusedBorderColor = InputBorder,
                errorBorderColor = ErrorRed,
                cursorColor = BrandBlue,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )
        if (isError && errorMessage != null) {
            Text(
                errorMessage,
                color = ErrorRed,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
        }
    }
}

// ── MediWise Social Auth Buttons ────────────────────────────────────────────
@Composable
fun MediWiseSocialRow(
    onGoogleClick: () -> Unit,
    onFacebookClick: () -> Unit,
    onBiometricClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Google Button
        Surface(
            onClick = onGoogleClick,
            shape = CircleShape,
            color = SurfaceWhite,
            border = BorderStroke(1.dp, SocialBorder),
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "G",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEA4335)
                )
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        // Facebook Button
        Surface(
            onClick = onFacebookClick,
            shape = CircleShape,
            color = SurfaceWhite,
            border = BorderStroke(1.dp, SocialBorder),
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "f",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1877F2)
                )
            }
        }

        if (onBiometricClick != null) {
            Spacer(modifier = Modifier.width(20.dp))
            // Biometric / Fingerprint / Shield Button
            Surface(
                onClick = onBiometricClick,
                shape = CircleShape,
                color = SurfaceWhite,
                border = BorderStroke(1.dp, SocialBorder),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "◎",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandBlue
                    )
                }
            }
        }
    }
}

// ── MediWise Minimal Top Bar ────────────────────────────────────────────────
@Composable
fun MediWiseTopBar(
    title: String = "",
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary
            )
        }
        if (title.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}

// ── Primary Button ─────────────────────────────────────────────────────────
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = PrimaryBlue
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled && !isLoading,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor)
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Text Field ─────────────────────────────────────────────────────────────
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            isError = isError,
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                focusedLabelColor = PrimaryBlue,
                cursorColor = PrimaryBlue
            )
        )
        if (isError && errorMessage != null) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        }
    }
}

// ── Card ───────────────────────────────────────────────────────────────────
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            content = content
        )
    }
}

// ── Skeleton Shimmer ───────────────────────────────────────────────────────
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val shimmerColor = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) {
        shimmerColor.animateTo(0.9f, infiniteRepeatable(
            animation = tween(1000), repeatMode = RepeatMode.Reverse
        ))
    }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color.LightGray.copy(alpha = shimmerColor.value))
    )
}

// ── Empty State ────────────────────────────────────────────────────────────
@Composable
fun EmptyStateCard(icon: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

// ── Quick Action Card ──────────────────────────────────────────────────────
@Composable
fun QuickActionCard(action: Any, onClick: () -> Unit) {
    AppCard(onClick = onClick, modifier = Modifier.width(80.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("❓", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

// ── Doctor Card ────────────────────────────────────────────────────────────
@Composable
fun DoctorCard(doctor: Doctor, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(PrimaryBlueLight),
                contentAlignment = Alignment.Center
            ) { Text("👨‍⚕️", style = MaterialTheme.typography.headlineSmall) }

            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(doctor.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(doctor.specialty, style = MaterialTheme.typography.bodySmall, color = PrimaryBlue)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐ ${doctor.avgRating}", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Text("${doctor.experienceYears}yr exp", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("₹${doctor.consultationFee}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier.background(
                        if (doctor.isAvailable) AccentGreenLight else ErrorRedLight,
                        MaterialTheme.shapes.small
                    ).padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (doctor.isAvailable) "Available" else "Unavailable",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (doctor.isAvailable) AccentGreen else ErrorRed
                    )
                }
            }
        }
    }
}

// ── Appointment Summary Card ───────────────────────────────────────────────
@Composable
fun AppointmentSummaryCard(appt: Appointment, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(PrimaryBlueLight),
                contentAlignment = Alignment.Center
            ) { Text("📅", style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(appt.doctorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(appt.date, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            StatusChip(status = appt.status)
        }
    }
}

// ── Status Chip ────────────────────────────────────────────────────────────
@Composable
fun StatusChip(status: String) {
    val (bg, fg) = when (status.uppercase()) {
        "CONFIRMED" -> AccentGreenLight to AccentGreen
        "PENDING" -> WarningAmberLight to WarningAmber
        "CANCELLED" -> ErrorRedLight to ErrorRed
        else -> SurfaceElevated to TextSecondary
    }
    Box(
        modifier = Modifier.background(bg, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp)
    ) { Text(status, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Medium) }
}

// ── Bottom Navigation Bar ──────────────────────────────────────────────────
@Composable
fun ClinicalBottomBar(
    navController: androidx.navigation.NavController,
    currentRoute: String,
    onTabReselected: (String) -> Unit = {}
) {
    val items = listOf(
        Triple("Home", "🏠", com.mediwise.presentation.navigation.Screen.Home.route),
        Triple("Doctors", "👨‍⚕️", com.mediwise.presentation.navigation.Screen.DoctorList.route),
        Triple("Appointments", "📅", com.mediwise.presentation.navigation.Screen.Appointments.route),
        Triple("Profile", "👤", com.mediwise.presentation.navigation.Screen.Profile.route)
    )

    NavigationBar(containerColor = SurfaceWhite) {
        items.forEach { (label, icon, route) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = {
                    if (currentRoute == route) {
                        onTabReselected(route)
                    } else {
                        navController.navigateToMainTab(route)
                    }
                },
                icon = { Text(icon, style = MaterialTheme.typography.titleMedium) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = PrimaryBlueLight)
            )
        }
    }
}
