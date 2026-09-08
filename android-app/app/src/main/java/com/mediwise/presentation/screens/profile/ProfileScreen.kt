package com.mediwise.presentation.screens.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mediwise.domain.model.Doctor
import com.mediwise.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onEditClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onConsultationHistoryClick: () -> Unit = {},
    refreshTick: Int = 0,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(refreshTick) {
        if (refreshTick != 0) viewModel.loadProfile()
    }

    // Doctor photos have no self-service upload endpoint yet (see ProfileViewModel.uploadAvatar) —
    // only wire up the picker for patients so tapping the avatar as a doctor does nothing silently
    // wrong rather than nothing visible at all.
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes()
                val mimeType = context.contentResolver.getType(it) ?: "image/jpeg"
                if (bytes != null) {
                    viewModel.uploadAvatar(bytes, mimeType)
                }
            } catch (_: Exception) {}
        }
    }

    val displayName = if (uiState.isDoctor) {
        uiState.doctorProfile?.fullName?.ifBlank { "Doctor" } ?: "Doctor"
    } else {
        uiState.profile?.fullName?.ifBlank { "User" } ?: "User"
    }
    val subtitle = if (uiState.isDoctor) {
        uiState.doctorProfile?.specialty?.takeIf { it.isNotBlank() } ?: "Complete your profile"
    } else {
        uiState.profile?.email?.takeIf { it.isNotBlank() } ?: "Complete your profile"
    }
    val avatarUrl = if (uiState.isDoctor) uiState.doctorProfile?.profileImage else uiState.profile?.profileImageUrl

    val initials = remember(displayName) {
        displayName.split(" ")
            .filter { it.isNotBlank() }
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
            .ifBlank { "U" }
    }

    Scaffold(
        containerColor = BackgroundWhite
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                        .background(Brush.horizontalGradient(colors = listOf(PrimaryBlue,PrimaryBlueDark)))
                        .padding(start =18.dp, end = 18.dp, top = 12.dp, bottom = 20.dp)
                ){

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ){

                        Text(
                            text = "My Profile",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.size(40.dp)
                        ){
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ){

                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha=0.22f))
                                .then(
                                    if (!uiState.isDoctor)
                                        Modifier.clickable { imagePickerLauncher.launch("image/*") }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ){
                            if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Profile Image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            }else{
                                Text(
                                    text = initials,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }

                            if(uiState.isUploadingImage){
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color.Black.copy(0.30f),
                                    shape = CircleShape
                                ){
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                        }

                        Column(
                            modifier = Modifier.padding(start = 16.dp).weight(1f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (uiState.isDoctor) "Dr. $displayName" else displayName,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (uiState.isDoctor && uiState.doctorProfile?.verified == true) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.Verified,
                                        contentDescription = "Verified",
                                        tint = AccentGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(2.dp))

                            Text(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable(onClick = onEditClick)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Edit",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isDoctor) {
                doctorProfileContent(uiState, onNotificationsClick, onSettingsClick)
            } else {
                patientProfileContent(uiState, context, onEditClick, onNotificationsClick, onSettingsClick, onConsultationHistoryClick)
            }

            item {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.logout()
                        onLogoutClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                ) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", color = ErrorRed, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.patientProfileContent(
    uiState: ProfileUiState,
    context: android.content.Context,
    onEditClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onConsultationHistoryClick: () -> Unit
) {
    val profile = uiState.profile

    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProfileStatCard("${uiState.totalAppointments}", "Appoint", Modifier.weight(1f))
            ProfileStatCard("${uiState.totalDoctors}", "Doctors", Modifier.weight(1f))
            ProfileStatCard(profile?.bloodType?.ifBlank { "--" } ?: "--", "Blood Type", Modifier.weight(1f))
            ProfileStatCard(uiState.calculatedAge, "Age", Modifier.weight(1f))
        }
    }

    item {
        ProfileSection(title = "Health Information") {
            ProfileMenuItem(
                icon = Icons.Default.FavoriteBorder,
                title = "Blood Group & Vitals",
                subtitle = "Blood Type: ${profile?.bloodType?.ifBlank { "Not set" } ?: "Not set"}"
            )
            ProfileMenuItem(
                icon = Icons.Default.CalendarToday,
                title = "Date of Birth",
                subtitle = profile?.dateOfBirth?.ifBlank { "Not set" } ?: "Not set"
            )
            ProfileMenuItem(
                icon = Icons.Default.PersonOutline,
                title = "Gender",
                subtitle = profile?.gender?.ifBlank { "Not set" } ?: "Not set"
            )
        }
    }

    item {
        ProfileSection(title = "Contact & Emergency") {
            if (!profile?.phone.isNullOrBlank()) {
                ProfileMenuItem(
                    icon = Icons.Default.Phone,
                    title = "Phone",
                    subtitle = profile?.phone ?: ""
                )
            }
            if (!profile?.address.isNullOrBlank()) {
                ProfileMenuItem(
                    icon = Icons.Default.LocationOn,
                    title = "Address",
                    subtitle = profile?.address ?: ""
                )
            }
            ProfileMenuItem(
                icon = Icons.Default.ContactPhone,
                title = "Emergency Contact",
                subtitle = if (!profile?.emergencyContact.isNullOrBlank())
                    "Tap to dial: ${profile?.emergencyContact}"
                else
                    "Tap Edit Profile to set emergency contact",
                onClick = {
                    profile?.emergencyContact?.takeIf { it.isNotBlank() }?.let { num ->
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))
                        context.startActivity(dialIntent)
                    } ?: onEditClick()
                }
            )
        }
    }

    item {
        ProfileSection(title = "Medical Records") {
            ProfileMenuItem(
                icon = Icons.Default.History,
                title = "Consultation History",
                subtitle = "View previous doctor notes, diagnoses and prescriptions",
                onClick = onConsultationHistoryClick
            )
        }
    }

    item {
        ProfileSection(title = "Account & Preferences") {
            ProfileMenuItem(
                icon = Icons.Default.Notifications,
                title = "Notifications & Reminders",
                subtitle = "Manage appointment & chat alerts",
                onClick = onNotificationsClick
            )
            ProfileMenuItem(
                icon = Icons.Default.Settings,
                title = "App Settings",
                subtitle = "Theme, biometrics, security",
                onClick = onSettingsClick
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.doctorProfileContent(
    uiState: ProfileUiState,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val doctor = uiState.doctorProfile

    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProfileStatCard("${uiState.totalAppointments}", "Appoint", Modifier.weight(1f))
            ProfileStatCard(
                doctor?.avgRating?.let { "%.1f".format(it) } ?: "--",
                "Rating",
                Modifier.weight(1f)
            )
            ProfileStatCard("${doctor?.totalReviews ?: 0}", "Reviews", Modifier.weight(1f))
            ProfileStatCard("${doctor?.experienceYears ?: 0}y", "Experience", Modifier.weight(1f))
        }
    }

    item {
        ProfileSection(title = "Professional Information") {
            ProfileMenuItem(
                icon = Icons.Default.MedicalServices,
                title = "Specialty",
                subtitle = doctor?.specialty?.ifBlank { "Not set" } ?: "Not set"
            )
            ProfileMenuItem(
                icon = Icons.Default.Payments,
                title = "Consultation Fee",
                subtitle = doctor?.consultationFee?.takeIf { it.isNotBlank() && it != "0" }
                    ?.let { "₹$it" } ?: "Not set"
            )
            ProfileMenuItem(
                icon = if (doctor?.isAvailable == true) Icons.Default.EventAvailable else Icons.Default.EventBusy,
                title = "Availability",
                subtitle = if (doctor?.isAvailable == true) "Accepting new bookings" else "Not accepting bookings"
            )
            ProfileMenuItem(
                icon = Icons.Default.VerifiedUser,
                title = "Verification Status",
                subtitle = if (doctor?.verified == true) "Verified by MediWise" else "Pending verification"
            )
        }
    }

    if (!doctor?.bio.isNullOrBlank()) {
        item {
            ProfileSection(title = "Bio") {
                Text(
                    doctor?.bio ?: "",
                    fontSize = 13.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }

    item {
        ProfileSection(title = "Account & Preferences") {
            // "My Schedule" is intentionally not duplicated here — it's the doctor's
            // primary bottom-nav tab (see ClinicalBottomBar), always one tap away.
            ProfileMenuItem(
                icon = Icons.Default.Notifications,
                title = "Notifications & Reminders",
                subtitle = "Manage appointment & chat alerts",
                onClick = onNotificationsClick
            )
            ProfileMenuItem(
                icon = Icons.Default.Settings,
                title = "App Settings",
                subtitle = "Theme, biometrics, security",
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun ProfileStatCard(value: String, label: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PrimaryBlue)
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = PrimaryBlueLight,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
