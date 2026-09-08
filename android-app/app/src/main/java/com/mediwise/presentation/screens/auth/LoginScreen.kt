package com.mediwise.presentation.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mediwise.presentation.components.MediWiseInputField
import com.mediwise.presentation.components.MediWisePillButton
import com.mediwise.presentation.components.MediWiseSocialRow
import com.mediwise.presentation.components.MediWiseTopBar
import com.mediwise.presentation.navigation.Screen
import com.mediwise.presentation.theme.*

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var emailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = LocalContext.current as? android.app.Activity
    val canUseBiometricLogin by viewModel.canUseBiometricLogin.collectAsStateWithLifecycle()
    val launchGoogleSignIn = rememberGoogleSignInLauncher(
        onToken = viewModel::loginWithGoogleToken,
        onError = { message -> viewModel.showError(message) }
    )
    val launchBiometricLogin = rememberBiometricLoginLauncher(
        canUseBiometricLogin = canUseBiometricLogin,
        onSuccess = viewModel::completeBiometricLogin,
        onError = { message -> viewModel.showError(message) }
    )

    LaunchedEffect(uiState.navigateToHome) {
        if (uiState.navigateToHome) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
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
                title = "Log In",
                onBackClick = { navController.navigateUp() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Greeting Headline
            Text(
                text = "Welcome",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = BrandBlue
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Sign in to access your consultations and records",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = SubtitleGray,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Email or Phone Input Field
            MediWiseInputField(
                value = emailOrPhone,
                onValueChange = { emailOrPhone = it },
                label = "Email or Mobile Number",
                placeholder = "example@example.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Password Input Field
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

            Spacer(modifier = Modifier.height(8.dp))

            // Forgot Password Link
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(
                    onClick = { navController.navigate(Screen.ResetPassword.route) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Forgot Password?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = BrandBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Log In Button
            MediWisePillButton(
                text = "Log In",
                onClick = {
                    if (!emailOrPhone.contains("@") && emailOrPhone.isNotBlank()) {
                        val normalizedPhone = emailOrPhone.trim().let { if (it.startsWith("+")) it else "+$it" }
                        if (activity != null) {
                            navController.navigate(Screen.OtpVerify.createRoute(normalizedPhone, "login"))
                        }
                    } else if (emailOrPhone.isNotBlank() && password.isNotBlank()) {
                        viewModel.loginWithEmailPassword(emailOrPhone, password)
                    }
                },
                isLoading = uiState.isLoading
            )

            Spacer(modifier = Modifier.height(28.dp))

            // "or sign in with" Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Divider)
                Text(
                    text = "  or sign in with  ",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SubtitleGray
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Divider)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Social Buttons (Google, Biometric)
            MediWiseSocialRow(
                onGoogleClick = launchGoogleSignIn,
                onBiometricClick = launchBiometricLogin
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Bottom Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Don't have an account? ",
                    fontSize = 14.sp,
                    color = SubtitleGray
                )
                TextButton(
                    onClick = { navController.navigate(Screen.Signup.route) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Sign Up",
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
