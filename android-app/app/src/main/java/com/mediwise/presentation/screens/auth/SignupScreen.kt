package com.mediwise.presentation.screens.auth

import android.app.DatePickerDialog
import android.icu.util.Calendar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediwise.presentation.components.MediWiseInputField
import com.mediwise.presentation.components.MediWisePillButton
import com.mediwise.presentation.components.MediWiseSocialRow
import com.mediwise.presentation.components.MediWiseTopBar
import com.mediwise.presentation.theme.*

@Composable
fun SignupScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToOtp: (String) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val launchGoogleSignIn = rememberGoogleSignInLauncher(
        onToken = viewModel::loginWithGoogleToken,
        onError = { message -> viewModel.showError(message) }
    )

    LaunchedEffect(uiState.navigateToHome) {
        if (uiState.navigateToHome) {
            onNavigateToLogin()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SurfaceWhite
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceWhite)
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Top Navigation Bar
            MediWiseTopBar(
                title = "New Account",
                onBackClick = onNavigateToLogin
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Full Name
            MediWiseInputField(
                value = fullName,
                onValueChange = { fullName = it },
                label = "Full name",
                placeholder = "example@example.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Password
            MediWiseInputField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                placeholder = "••••••••••••",
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = InputPlaceholder
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Email
            MediWiseInputField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                placeholder = "example@example.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Mobile Number
            MediWiseInputField(
                value = mobileNumber,
                onValueChange = { mobileNumber = it },
                label = "Mobile Number",
                placeholder = "example@example.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Date of Birth

            val context = LocalContext.current
            val calendar = remember { Calendar.getInstance() }
            val datePickerDialog = remember {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val d = String.format("%02d", dayOfMonth)
                        val m = String.format("%02d", month + 1)
                        dob = "$d / $m / $year"
                    },
                    calendar.get(Calendar.YEAR) - 18,
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { datePickerDialog.show() }
            ) {
                MediWiseInputField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = "Date Of Birth",
                    placeholder = "DD / MM / YYYY",
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { datePickerDialog.show() }) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Select Date",
                                tint = BrandBlue
                            )
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { datePickerDialog.show() }
                )
            }


            Spacer(modifier = Modifier.height(16.dp))

            // Terms Agreement Text
            Text(
                text = "By creating an account, you agree to our Terms of Use and Privacy Policy.",
                fontSize = 12.sp,
                color = SubtitleGray,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Sign Up Button
            MediWisePillButton(
                text = "Sign Up",
                onClick = {
                    if (email.isNotBlank()) {
                        viewModel.registerWithEmailPassword(
                            fullName = fullName,
                            email = email,
                            password = password,
                            phone = mobileNumber.ifBlank { null },
                            dateOfBirth = dob.toBackendDate()
                        )
                    } else if (mobileNumber.isNotBlank()) {
                        onNavigateToOtp(mobileNumber)
                    }
                },
                isLoading = uiState.isLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            // "or sign up with" Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Divider)
                Text(
                    text = "  or sign up with  ",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SubtitleGray
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Divider)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Social Row
            MediWiseSocialRow(
                onGoogleClick = launchGoogleSignIn
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already have an account? ",
                    fontSize = 14.sp,
                    color = SubtitleGray
                )
                TextButton(
                    onClick = onNavigateToLogin,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Log In",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun String.toBackendDate(): String? {
    val parts = trim().split("/").map { it.trim() }
    if (parts.size != 3) return takeIf { it.isNotBlank() }
    return "${parts[2].padStart(4, '0')}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}"
}
