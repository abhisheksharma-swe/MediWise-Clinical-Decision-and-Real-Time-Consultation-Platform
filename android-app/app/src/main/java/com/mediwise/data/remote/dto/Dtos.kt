package com.mediwise.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponseDto<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val code: String? = null,
    @SerialName("correlationId") val correlationId: String? = null
)

@Serializable
data class PagedResponseDto<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean
)

@Serializable
data class RegisterRequestDto(
    val firebaseIdToken: String? = null,
    val email: String,
    val password: String? = null,
    val phone: String? = null,
    val role: String = "PATIENT",
    val fullName: String? = null,
    val dateOfBirth: String? = null
)
@Serializable
data class LoginRequestDto(
    val firebaseIdToken: String? = null,
    val emailOrPhone: String? = null,
    val password: String? = null
)
@Serializable
data class AuthResponseDto(val accessToken: String, val refreshToken: String, val expiresIn: Long, val user: UserInfoDto)
@Serializable
data class UserInfoDto(
    val id: String,
    val email: String,
    val phone: String? = null,
    val role: String,
    val fullName: String? = null,
    val profileId: String? = null,
    val specialty: String? = null
)

@Serializable
data class DoctorDto(
    val id: String, val userId: String, val fullName: String,
    val bio: String? = null, val specialty: String,
    val specialties: List<String> = emptyList(),
    val experienceYears: Int? = null,
    val consultationFee: Double? = null,
    val profileImage: String? = null,
    val avgRating: Double? = null,
    val totalReviews: Int = 0,
    val available: Boolean = true,
    val verified: Boolean = false
)

@Serializable
data class AppointmentDto(
    val id: String, val patientId: String, val doctorId: String,
    val doctorName: String? = null, val doctorSpecialty: String? = null, val doctorProfileImage: String? = null,
    val slotId: String, val slotDate: String? = null,
    val slotStartTime: String? = null, val slotEndTime: String? = null,
    val status: String, val type: String,
    val chiefComplaint: String? = null, val notes: String? = null,
    val diagnosis: String? = null, val prescription: String? = null,
    val cancelReason: String? = null, val createdAt: String? = null
)
@Serializable data class BookAppointmentRequestDto(val slotId: String, val doctorId: String, val type: String = "ONLINE", val chiefComplaint: String? = null)
@Serializable data class CancelRequestDto(val reason: String? = null)

@Serializable
data class SlotDto(
    val id: String,
    val doctorId: String? = null,
    val slotDate: String,
    val startTime: String,
    val endTime: String,
    val status: String
)

@Serializable
data class SlotLockResponseDto(
    val slotId: String,
    val locked: Boolean,
    val expiresAt: String,
    val ttlMinutes: Long = 5
)

@Serializable
data class PaymentDto(
    val id: String,
    val appointmentId: String,
    val amount: Double? = null,
    val currency: String = "INR",
    val status: String,
    val gatewayOrderId: String? = null,
    val gatewayPaymentId: String? = null
)
@Serializable data class InitiatePaymentRequestDto(val appointmentId: String, val amount: String? = null)
@Serializable data class VerifyPaymentRequestDto(val razorpayOrderId: String, val razorpayPaymentId: String, val razorpaySignature: String)

@Serializable data class ProfileDto(val id: String? = null, val userId: String? = null, val fullName: String? = null, val dob: String? = null, val bloodType: String? = null, val gender: String? = null, val address: String? = null, val emergencyContact: String? = null, val profileImage: String? = null)
@Serializable data class UpdateProfileRequestDto(val fullName: String? = null, val dob: String? = null, val bloodType: String? = null, val gender: String? = null, val address: String? = null, val emergencyContact: String? = null)

@Serializable data class NotificationDto(val id: String, val title: String, val body: String? = null, val type: String, val read: Boolean = false, val sentAt: String? = null)

@Serializable data class ChatMessageDto(val id: String? = null, val roomId: String, val senderId: String, val senderRole: String, val content: String, val contentType: String = "TEXT", val sentAt: String? = null, val read: Boolean = false)

@Serializable
data class AppConfigDto(
    val appName: String? = null,
    val version: String? = null,
    val status: String? = null,
    val environment: String? = null,
    val features: List<String> = emptyList()
)

@Serializable
data class ForgotPasswordRequestDto(val emailOrPhone: String)

@Serializable
data class ResetPasswordRequestDto(val emailOrPhone: String, val token: String? = null, val newPassword: String)

@Serializable
data class ChangePasswordRequestDto(val currentPassword: String, val newPassword: String)

@Serializable
data class SymptomLogRequestDto(
    val patientId: String,
    val appointmentId: String? = null,
    val symptoms: List<String>,
    val severity: String? = null,
    val heartRate: Int? = null,
    val systolicBp: Int? = null,
    val diastolicBp: Int? = null,
    val spo2: Double? = null,
    val temperature: Double? = null,
    val notes: String? = null
)

@Serializable
data class AiReportDto(
    val id: String? = null,
    val patientId: String? = null,
    val appointmentId: String? = null,
    val modelName: String? = null,
    val modelVersion: String? = null,
    val urgencyScore: Int = 0,
    val suggestedSpecialty: String? = null,
    val confidence: Double = 0.0,
    val recommendation: String? = null,
    val riskFactors: List<String> = emptyList(),
    val createdAt: String? = null
)

fun DoctorDto.toDomain() = com.mediwise.domain.model.Doctor(
    id = id,
    fullName = fullName,
    specialty = specialty,
    consultationFee = consultationFee?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "0",
    rating = avgRating ?: 0.0,
    reviewCount = totalReviews,
    profileImage = profileImage,
    available = available,
    bio = bio ?: "",
    avgRating = avgRating ?: 0.0,
    totalReviews = totalReviews,
    experienceYears = experienceYears ?: 0,
    isAvailable = available
)

fun AppointmentDto.toDomain() = com.mediwise.domain.model.Appointment(
    id = id,
    patientId = patientId,
    doctorId = doctorId,
    doctorName = doctorName ?: "",
    doctorSpecialty = doctorSpecialty ?: "",
    slotId = slotId,
    date = slotDate ?: "",
    time = if (slotStartTime != null && slotEndTime != null) "$slotStartTime - $slotEndTime" else (slotStartTime ?: ""),
    status = status,
    type = type
)

fun SlotDto.toDomain() = com.mediwise.domain.model.SlotModel(
    id = id,
    doctorId = doctorId ?: "",
    date = slotDate,
    startTime = startTime,
    endTime = endTime,
    status = status
)

fun AuthResponseDto.toDomain() = com.mediwise.domain.model.AuthResult(accessToken = accessToken, refreshToken = refreshToken, user = user.toDomain())
fun UserInfoDto.toDomain() = com.mediwise.domain.model.User(id = id, email = email, role = role)
fun PaymentDto.toDomain() = com.mediwise.domain.model.Payment(
    id = id,
    appointmentId = appointmentId,
    amount = amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "0",
    status = status
)
fun ProfileDto.toDomain() = com.mediwise.domain.model.UserProfile(fullName = fullName ?: "", dob = dob ?: "", bloodType = bloodType ?: "", gender = gender ?: "", profileImage = profileImage)
fun ProfileDto.toPatientProfile(email: String = "", phone: String = "") = com.mediwise.domain.model.PatientProfile(
    id = id ?: "",
    fullName = fullName ?: "",
    email = email,
    phone = phone,
    dateOfBirth = dob ?: "",
    gender = gender ?: "",
    bloodType = bloodType ?: "",
    address = address ?: "",
    emergencyContact = emergencyContact ?: "",
    profileImageUrl = profileImage
)
fun NotificationDto.toDomain() = com.mediwise.domain.model.Notification(id = id, title = title, body = body ?: "", type = type, isRead = read, time = sentAt ?: "")
fun ChatMessageDto.toDomain() = com.mediwise.domain.model.ChatMessage(id = id ?: "", senderId = senderId, content = content, time = sentAt ?: "", isMe = false)
fun AiReportDto.toDomain() = com.mediwise.domain.model.AiTriageReport(
    id = id ?: "",
    patientId = patientId ?: "",
    urgencyScore = urgencyScore,
    suggestedSpecialty = suggestedSpecialty ?: "",
    confidence = confidence,
    recommendation = recommendation ?: "",
    riskFactors = riskFactors,
    createdAt = createdAt ?: ""
)

