package com.mediwise.presentation.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediwise.presentation.components.MediWiseInputField
import com.mediwise.presentation.components.MediWisePillButton
import com.mediwise.presentation.components.MediWiseTopBar
import com.mediwise.presentation.theme.*

@Composable
fun OtpScreen(
    phone: String,
    mode: String,
    onVerificationSuccess: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var otp by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = LocalContext.current as? android.app.Activity

    LaunchedEffect(phone, mode) {
        if (activity != null) {
            viewModel.startPhoneVerification(activity, phone, mode)
        }
    }

    LaunchedEffect(uiState.navigateToHome) {
        if (uiState.navigateToHome) {
            onVerificationSuccess()
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
                title = "Verification",
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Enter Verification Code",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BrandBlue
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "We have sent a 6-digit verification code to $phone",
                fontSize = 14.sp,
                color = SubtitleGray,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            MediWiseInputField(
                value = otp,
                onValueChange = { if (it.length <= 6) otp = it },
                label = "Security Code",
                placeholder = "123456",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(32.dp))

            MediWisePillButton(
                text = "Verify Code",
                onClick = { viewModel.verifyPhoneCode(otp, mode, phone) },
                isLoading = uiState.isLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Didn't receive code? ",
                    fontSize = 14.sp,
                    color = SubtitleGray
                )
                TextButton(
                    onClick = { /* Resend OTP */ },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Resend",
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
