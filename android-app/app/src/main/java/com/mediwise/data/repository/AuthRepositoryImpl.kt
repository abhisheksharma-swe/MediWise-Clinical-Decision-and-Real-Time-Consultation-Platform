package com.mediwise.data.repository

import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.core.network.safeApiCall
import com.mediwise.core.result.Result
import com.mediwise.data.remote.api.AuthApi
import com.mediwise.data.remote.dto.LoginRequestDto
import com.mediwise.data.remote.dto.RegisterRequestDto
import com.mediwise.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.mediwise.data.remote.dto.AppConfigDto
import com.mediwise.data.remote.dto.ChangePasswordRequestDto
import com.mediwise.data.remote.dto.ForgotPasswordRequestDto
import kotlinx.coroutines.tasks.await
import com.mediwise.data.remote.dto.ResetPasswordRequestDto
import com.mediwise.data.remote.dto.UserInfoDto
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val sessionDataStore: SessionDataStore
) : AuthRepository {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun login(firebaseIdToken: String): Result<Unit> {
        return safeApiCall {
            val response = api.login(LoginRequestDto(firebaseIdToken = firebaseIdToken))
            val data = response.data ?: throw Exception("Empty response")
            sessionDataStore.saveSession(
                data.accessToken, data.refreshToken,
                data.user.id, data.user.role, data.user.email
            )
        }
    }

    override suspend fun loginWithEmailPassword(email: String, password: String): Result<Unit> {
        return safeApiCall {
            try {
                // First attempt direct backend authentication (supports Doctor, Admin, and backend-registered Patients)
                val response = api.login(LoginRequestDto(emailOrPhone = email.trim(), password = password))
                val data = response.data ?: throw Exception("Empty response")
                sessionDataStore.saveSession(data.accessToken, data.refreshToken, data.user.id, data.user.role, data.user.email)
            } catch (backendEx: Exception) {
                // If backend direct auth fails, attempt Firebase auth fallback
                try {
                    val firebaseUser = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await().user
                        ?: throw backendEx
                    val token = firebaseUser.getIdToken(true).await().token
                        ?: throw backendEx
                    val response = api.login(LoginRequestDto(firebaseIdToken = token))
                    val data = response.data ?: throw Exception("Empty response")
                    sessionDataStore.saveSession(data.accessToken, data.refreshToken, data.user.id, data.user.role, data.user.email)
                } catch (firebaseEx: Exception) {
                    throw backendEx
                }
            }
        }
    }

    override suspend fun loginWithPhoneCredential(credential: com.google.firebase.auth.PhoneAuthCredential): Result<Unit> {
        return safeApiCall {
            val firebaseUser = firebaseAuth.signInWithCredential(credential).await().user
                ?: throw Exception("Firebase user was not returned")
            val token = firebaseUser.getIdToken(true).await().token
                ?: throw Exception("Firebase ID token was not returned")
            val response = api.login(LoginRequestDto(firebaseIdToken = token))
            val data = response.data ?: throw Exception("Account not found. Please register first.")
            sessionDataStore.saveSession(data.accessToken, data.refreshToken, data.user.id, data.user.role, data.user.email)
        }
    }

    override suspend fun register(firebaseIdToken: String, email: String, phone: String, role: String): Result<Unit> {
        return safeApiCall {
            val response = api.register(RegisterRequestDto(firebaseIdToken = firebaseIdToken, email = email, phone = phone, role = role))
            val data = response.data ?: throw Exception("Empty response")
            sessionDataStore.saveSession(
                data.accessToken, data.refreshToken,
                data.user.id, data.user.role, data.user.email
            )
        }
    }

    override suspend fun registerWithEmailPassword(fullName: String, email: String, password: String, phone: String?, dateOfBirth: String?): Result<Unit> {
        return safeApiCall {
            var token: String? = null
            try {
                val firebaseUser = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await().user
                firebaseUser?.sendEmailVerification()?.await()
                token = firebaseUser?.getIdToken(true)?.await()?.token
            } catch (ignored: Exception) {
                // Firebase might not be configured or available, proceed with direct backend registration
            }
            val response = api.register(
                RegisterRequestDto(
                    firebaseIdToken = token,
                    email = email.trim(),
                    password = password,
                    phone = phone,
                    role = "PATIENT",
                    fullName = fullName.trim(),
                    dateOfBirth = dateOfBirth
                )
            )
            val data = response.data ?: throw Exception("Empty response")
            sessionDataStore.saveSession(data.accessToken, data.refreshToken, data.user.id, data.user.role, data.user.email)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return safeApiCall {
            try {
                // First try backend forgot-password endpoint
                api.forgotPassword(ForgotPasswordRequestDto(email.trim()))
            } catch (e: Exception) {
                // Fallback to Firebase password reset if backend call fails
                firebaseAuth.sendPasswordResetEmail(email.trim()).await()
            }
        }
    }

    override suspend fun resetPassword(emailOrPhone: String, token: String?, newPassword: String): Result<Unit> {
        return safeApiCall {
            api.resetPassword(ResetPasswordRequestDto(emailOrPhone = emailOrPhone.trim(), token = token, newPassword = newPassword))
        }
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return safeApiCall {
            api.changePassword(ChangePasswordRequestDto(currentPassword = currentPassword, newPassword = newPassword))
        }
    }

    override suspend fun getCurrentUser(): Result<UserInfoDto> {
        return safeApiCall {
            val response = api.getCurrentUser()
            response.data ?: throw Exception("User profile not found")
        }
    }

    override suspend fun getAppConfig(): Result<AppConfigDto> {
        return safeApiCall {
            val response = api.getAppConfig()
            response.data ?: throw Exception("Configuration not found")
        }
    }

    override suspend fun logout(): Result<Unit> {
        return safeApiCall {
            try {
                val token = sessionDataStore.accessToken.first()
                if (!token.isNullOrBlank()) {
                    api.logout("Bearer $token")
                }
            } catch (ignored: Exception) {}
            try {
                firebaseAuth.signOut()
            } catch (ignored: Exception) {}
            sessionDataStore.clearSession()
        }
    }

    override suspend fun refreshToken(): Result<Unit> {
        return safeApiCall {
            val refreshToken = sessionDataStore.refreshToken.first()
                ?: throw Exception("No refresh token available")
            val response = api.refresh(refreshToken)
            val data = response.data ?: throw Exception("Failed to refresh token")
            sessionDataStore.saveSession(
                data.accessToken, data.refreshToken,
                data.user.id, data.user.role, data.user.email
            )
        }
    }
}
