package com.mediwise.presentation.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mediwise.presentation.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Patient- and doctor-form state are independent — only one is ever active per
    // account, but both are declared unconditionally so hooks stay stable across the
    // `uiState.isDoctor` branch (the role is already known before this screen is reachable).
    val profile = uiState.profile
    var fullName by remember(profile?.fullName) { mutableStateOf(profile?.fullName ?: "") }
    var phone by remember(profile?.phone) { mutableStateOf(profile?.phone ?: "") }
    var dob by remember(profile?.dateOfBirth) { mutableStateOf(profile?.dateOfBirth ?: "") }
    var gender by remember(profile?.gender) { mutableStateOf(profile?.gender ?: "") }
    var bloodType by remember(profile?.bloodType) { mutableStateOf(profile?.bloodType ?: "") }
    var address by remember(profile?.address) { mutableStateOf(profile?.address ?: "") }
    var emergencyContact by remember(profile?.emergencyContact) { mutableStateOf(profile?.emergencyContact ?: "") }

    val doctor = uiState.doctorProfile
    var doctorName by remember(doctor?.fullName) { mutableStateOf(doctor?.fullName ?: "") }
    var specialty by remember(doctor?.specialty) { mutableStateOf(doctor?.specialty ?: "") }
    var experienceYears by remember(doctor?.experienceYears) { mutableStateOf(doctor?.experienceYears?.takeIf { it > 0 }?.toString() ?: "") }
    var consultationFee by remember(doctor?.consultationFee) { mutableStateOf(doctor?.consultationFee?.takeIf { it != "0" } ?: "") }
    var bio by remember(doctor?.bio) { mutableStateOf(doctor?.bio ?: "") }
    var available by remember(doctor?.isAvailable) { mutableStateOf(doctor?.isAvailable ?: true) }

    val displayName = if (uiState.isDoctor) doctorName else fullName
    val avatarUrl = if (uiState.isDoctor) doctor?.profileImage else profile?.profileImageUrl

    val context = LocalContext.current
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
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (uiState.isDoctor) {
                                if (doctorName.isBlank()) {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Please enter your full name") }
                                    return@TextButton
                                }
                                if (specialty.isBlank()) {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Please enter your specialty") }
                                    return@TextButton
                                }
                                if (consultationFee.toDoubleOrNull() == null || consultationFee.toDouble() <= 0.0) {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Please enter a valid consultation fee") }
                                    return@TextButton
                                }
                                viewModel.updateDoctorProfile(
                                    fullName = doctorName,
                                    specialty = specialty,
                                    bio = bio,
                                    experienceYears = experienceYears,
                                    consultationFee = consultationFee,
                                    available = available
                                ) { success ->
                                    if (success) {
                                        onSaveClick()
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(uiState.error ?: "Failed to save profile")
                                        }
                                    }
                                }
                            } else {
                                if (fullName.isBlank()) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Please enter your full name")
                                    }
                                    return@TextButton
                                }
                                viewModel.updateProfile(
                                    fullName = fullName,
                                    dob = dob,
                                    bloodType = bloodType,
                                    gender = gender,
                                    address = address,
                                    emergencyContact = emergencyContact
                                ) { success ->
                                    if (success) {
                                        onSaveClick()
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(uiState.error ?: "Failed to save profile")
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryBlue
                            )
                        } else {
                            Text("Save", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWhite)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundWhite
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // Avatar change section — doctor photos have no self-service upload endpoint
            // yet, so the camera overlay only appears (and is only clickable) for patients.
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    initials,
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp
                                )
                            }
                        }
                        if (!uiState.isDoctor) {
                            Surface(
                                shape = CircleShape,
                                color = PrimaryBlue,
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(28.dp)
                                    .clickable { imagePickerLauncher.launch("image/*") }
                            ) {
                                if (uiState.isUploadingImage) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = "Upload Photo",
                                        tint = Color.White,
                                        modifier = Modifier.padding(6.dp).size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.isDoctor) {
                doctorEditContent(
                    doctorName = doctorName, onDoctorNameChange = { doctorName = it },
                    specialty = specialty, onSpecialtyChange = { specialty = it },
                    experienceYears = experienceYears, onExperienceChange = { experienceYears = it },
                    consultationFee = consultationFee, onFeeChange = { consultationFee = it },
                    bio = bio, onBioChange = { bio = it },
                    available = available, onAvailableChange = { available = it }
                )
            } else {
                patientEditContent(
                    fullName = fullName, onFullNameChange = { fullName = it },
                    phone = phone,
                    dob = dob, onDobChange = { dob = it },
                    gender = gender, onGenderChange = { gender = it },
                    bloodType = bloodType, onBloodTypeChange = { bloodType = it },
                    address = address, onAddressChange = { address = it },
                    emergencyContact = emergencyContact, onEmergencyContactChange = { emergencyContact = it }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.patientEditContent(
    fullName: String, onFullNameChange: (String) -> Unit,
    phone: String,
    dob: String, onDobChange: (String) -> Unit,
    gender: String, onGenderChange: (String) -> Unit,
    bloodType: String, onBloodTypeChange: (String) -> Unit,
    address: String, onAddressChange: (String) -> Unit,
    emergencyContact: String, onEmergencyContactChange: (String) -> Unit
) {
    item {
        SectionHeader("Personal Information")
        ProfileTextField("Full Name", fullName, Icons.Default.Person, onValueChange = onFullNameChange)
        Spacer(Modifier.height(12.dp))
        ProfileTextField(
            "Phone Number",
            phone,
            Icons.Default.Phone,
            readOnly = true,
            placeholder = "Not set",
            onValueChange = {}
        )
        if (phone.isNotBlank()) {
            Text(
                "Phone number is verified at sign-in and can't be changed here.",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        ProfileTextField(
            "Date of Birth (YYYY-MM-DD)",
            dob,
            Icons.Default.CalendarMonth,
            placeholder = "YYYY-MM-DD",
            onValueChange = onDobChange
        )
    }

    item {
        SectionHeader("Health Details")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GenderSelector(
                selected = gender,
                onSelect = onGenderChange,
                modifier = Modifier.weight(1f)
            )
            BloodTypeSelector(
                selected = bloodType,
                onSelect = onBloodTypeChange,
                modifier = Modifier.weight(1f)
            )
        }
    }

    item {
        SectionHeader("Location & Emergency")
        ProfileTextField("Address", address, Icons.Default.LocationOn, placeholder = "e.g. 123 Healthcare Ave, Mumbai", onValueChange = onAddressChange)
        Spacer(Modifier.height(12.dp))
        ProfileTextField(
            "Emergency Contact",
            emergencyContact,
            Icons.Default.ContactPhone,
            placeholder = "e.g. +91 98765 43210",
            onValueChange = onEmergencyContactChange
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.doctorEditContent(
    doctorName: String, onDoctorNameChange: (String) -> Unit,
    specialty: String, onSpecialtyChange: (String) -> Unit,
    experienceYears: String, onExperienceChange: (String) -> Unit,
    consultationFee: String, onFeeChange: (String) -> Unit,
    bio: String, onBioChange: (String) -> Unit,
    available: Boolean, onAvailableChange: (Boolean) -> Unit
) {
    item {
        SectionHeader("Personal Information")
        ProfileTextField("Full Name", doctorName, Icons.Default.Person, onValueChange = onDoctorNameChange)
    }

    item {
        SectionHeader("Professional Details")
        ProfileTextField(
            "Specialty",
            specialty,
            Icons.Default.MedicalServices,
            placeholder = "e.g. Cardiology",
            onValueChange = onSpecialtyChange
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileTextField(
                "Experience (years)",
                experienceYears,
                Icons.Default.WorkHistory,
                placeholder = "e.g. 5",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
                onValueChange = { value -> onExperienceChange(value.filter { it.isDigit() }) }
            )
            ProfileTextField(
                "Consultation Fee (₹)",
                consultationFee,
                Icons.Default.Payments,
                placeholder = "e.g. 500",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
                onValueChange = { value -> onFeeChange(value.filter { it.isDigit() || it == '.' }) }
            )
        }
    }

    item {
        SectionHeader("Bio")
        OutlinedTextField(
            value = bio,
            onValueChange = onBioChange,
            placeholder = { Text("Tell patients about your background and approach to care", color = TextSecondary) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = Divider,
                focusedLabelColor = PrimaryBlue
            ),
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth()
        )
    }

    item {
        SectionHeader("Availability")
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Accepting new bookings", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text(
                        if (available) "Patients can book appointments with you" else "You won't appear as bookable to patients",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = available,
                    onCheckedChange = onAvailableChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue, checkedTrackColor = PrimaryBlueLight)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        modifier = Modifier.padding(bottom = 12.dp, top = 4.dp)
    )
}

@Composable
private fun ProfileTextField(
    label: String,
    value: String,
    icon: ImageVector,
    placeholder: String = label,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = TextSecondary) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = PrimaryBlue) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryBlue,
            unfocusedBorderColor = Divider,
            focusedLabelColor = PrimaryBlue
        ),
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun GenderSelector(selected: String, onSelect: (String) -> Unit, modifier: Modifier) {
    Column(modifier = modifier) {
        Text("Gender", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Male", "Female", "Other").forEach { option ->
                val isSelected = selected.equals(option, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) PrimaryBlue else SurfaceWhite,
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        option,
                        fontSize = 12.sp,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BloodTypeSelector(selected: String, onSelect: (String) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val types = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
    Column(modifier = modifier) {
        Text("Blood Type", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Select", color = TextSecondary) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = Divider
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                types.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type) },
                        onClick = { onSelect(type); expanded = false }
                    )
                }
            }
        }
    }
}
