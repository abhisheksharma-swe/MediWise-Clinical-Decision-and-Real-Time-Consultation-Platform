package com.mediwise.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.core.result.Result
import com.mediwise.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val navigateToHome: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    sessionDataStore: SessionDataStore
) : ViewModel() {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private var phoneVerificationId: String? = null

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /**
     * Whether biometric unlock should be offered: a real session is stored locally AND the
     * user opted into "Biometric Login" in Settings (see [SessionDataStore.biometricLogin]).
     */
    val canUseBiometricLogin: StateFlow<Boolean> = combine(
        sessionDataStore.isLoggedIn,
        sessionDataStore.biometricLogin
    ) { loggedIn, biometricEnabled -> loggedIn && biometricEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun login(firebaseToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.login(firebaseToken)
            if (result is Result.Success) {
                _uiState.update { it.copy(isLoading = false, isSuccess = true, navigateToHome = true) }
            } else if (result is Result.Error) {
                _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
            }
        }
    }

    fun loginWithEmailPassword(email: String, pass: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            updateState(repository.loginWithEmailPassword(email, pass))
        }
    }

    /** Called with a real Google ID token obtained from [rememberGoogleSignInLauncher]. */
    fun loginWithGoogleToken(idToken: String) = login(idToken)

    /**
     * Called once a real [androidx.biometric.BiometricPrompt] authentication succeeds.
     * Never issues a new login - it only unlocks the app using the session that is
     * already stored from a prior real login (see [hasStoredSession]).
     */
    fun completeBiometricLogin() {
        _uiState.update { it.copy(isLoading = false, isSuccess = true, navigateToHome = true) }
    }

    fun showError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    /** Starts Firebase phone verification for either the login or the registration flow. */
    fun startPhoneVerification(activity: Activity, phone: String, mode: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                viewModelScope.launch { completePhoneAuth(credential, mode, phone) }
            }

            override fun onVerificationFailed(exception: FirebaseException) {
                showError(exception.localizedMessage ?: "Could not send verification code.")
                _uiState.update { it.copy(isLoading = false) }
            }

            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                phoneVerificationId = id
                _uiState.update { it.copy(isLoading = false) }
            }
        }
        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phone)
            .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /** Verifies the OTP the user entered, for either the login or the registration flow. */
    fun verifyPhoneCode(code: String, mode: String, phone: String) {
        val id = phoneVerificationId
        if (id.isNullOrBlank()) {
            showError("Verification expired. Please request a new code.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            completePhoneAuth(PhoneAuthProvider.getCredential(id, code), mode, phone)
        }
    }

    private suspend fun completePhoneAuth(credential: PhoneAuthCredential, mode: String, phone: String) {
        if (mode == "login") {
            updateState(repository.loginWithPhoneCredential(credential))
            return
        }
        try {
            val firebaseUser = firebaseAuth.signInWithCredential(credential).await().user
                ?: throw Exception("Phone verification failed. Please try again.")
            val idToken = firebaseUser.getIdToken(true).await().token
                ?: throw Exception("Could not verify your phone number. Please try again.")
            updateState(repository.register(idToken, "$phone@email.com", phone, "PATIENT"))
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = e.message ?: "Phone verification failed.") }
        }
    }

    fun registerWithEmailPassword(fullName: String, email: String, password: String, phone: String?, dateOfBirth: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            updateState(repository.registerWithEmailPassword(fullName, email, password, phone, dateOfBirth))
        }
    }

    fun sendPasswordResetEmail(email: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.sendPasswordResetEmail(email)) {
                is Result.Success -> onComplete(true, null)
                is Result.Error -> onComplete(false, result.exception.message)
                Result.Loading -> Unit
            }
        }
    }

    private fun updateState(result: Result<Unit>) {
        if (result is Result.Success) {
            _uiState.update { it.copy(isLoading = false, isSuccess = true, navigateToHome = true) }
        } else if (result is Result.Error) {
            _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
        }
    }

    fun resetState() {
        _uiState.update { AuthUiState() }
    }
}
