package com.mediwise.presentation.screens.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Launches a real [BiometricPrompt] and, on success, unlocks the app using the session
 * already stored from a prior real login - it never performs a new/fake login.
 *
 * Requires a [FragmentActivity] host, which [com.mediwise.presentation.MainActivity]
 * provides specifically for this purpose.
 */
@Composable
fun rememberBiometricLoginLauncher(
    canUseBiometricLogin: Boolean,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val activity = LocalContext.current as? FragmentActivity

    return remember(canUseBiometricLogin, activity) {
        {
            if (activity == null) {
                onError("Biometric sign-in is unavailable in this context.")
            } else if (!canUseBiometricLogin) {
                onError("Turn on Biometric Login in Settings after signing in once to use this.")
            } else {
                val biometricManager = BiometricManager.from(activity)
                when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                    BiometricManager.BIOMETRIC_SUCCESS -> {
                        val executor = ContextCompat.getMainExecutor(activity)
                        val prompt = BiometricPrompt(
                            activity,
                            executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    onSuccess()
                                }

                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                    ) {
                                        onError(errString.toString())
                                    }
                                }
                            }
                        )
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Biometric Sign-In")
                            .setSubtitle("Use your fingerprint or face to continue")
                            .setNegativeButtonText("Cancel")
                            .build()
                        prompt.authenticate(promptInfo)
                    }
                    else -> onError("Biometric authentication is not set up on this device.")
                }
            }
        }
    }
}
