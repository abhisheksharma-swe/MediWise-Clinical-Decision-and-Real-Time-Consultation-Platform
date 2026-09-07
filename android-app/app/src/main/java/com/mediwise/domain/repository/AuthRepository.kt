package com.mediwise.domain.repository

import com.google.firebase.auth.PhoneAuthCredential
import com.mediwise.core.result.Result
import com.mediwise.data.remote.dto.AppConfigDto
import com.mediwise.data.remote.dto.UserInfoDto

interface AuthRepository {
    suspend fun login(firebaseIdToken: String): Result<Unit>
    suspend fun loginWithEmailPassword(email: String, password: String): Result<Unit>
    suspend fun loginWithPhoneCredential(credential: PhoneAuthCredential): Result<Unit>
    suspend fun register(firebaseIdToken: String, email: String, phone: String, role: String): Result<Unit>
    suspend fun registerWithEmailPassword(fullName: String, email: String, password: String, phone: String?, dateOfBirth: String?): Result<Unit>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun resetPassword(emailOrPhone: String, token: String?, newPassword: String): Result<Unit>
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
    suspend fun getCurrentUser(): Result<UserInfoDto>
    suspend fun getAppConfig(): Result<AppConfigDto>
    suspend fun logout(): Result<Unit>
    suspend fun refreshToken(): Result<Unit>
}
