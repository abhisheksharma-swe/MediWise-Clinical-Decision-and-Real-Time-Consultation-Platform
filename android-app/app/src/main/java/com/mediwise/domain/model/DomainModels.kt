package com.mediwise.domain.model

data class User(val id: String, val email: String, val role: String)
data class AuthResult(val accessToken: String, val refreshToken: String, val user: User)
data class Doctor(val id: String, val fullName: String, val specialty: String, val consultationFee: String, val rating: Double, val reviewCount: Int, val profileImage: String?, val available: Boolean, val bio: String = "", val avgRating: Double = 0.0, val totalReviews: Int = 0, val experienceYears: Int = 0, val isAvailable: Boolean = true)
data class Appointment(val id: String, val patientId: String, val doctorId: String, val doctorName: String = "", val doctorSpecialty: String = "", val slotId: String, val date: String, val time: String, val status: String, val type: String)
data class Payment(val id: String, val appointmentId: String, val amount: String, val status: String)
data class UserProfile(val fullName: String, val dob: String, val bloodType: String, val gender: String, val profileImage: String?)
data class Notification(val id: String, val title: String, val body: String, val type: String, val isRead: Boolean, val time: String)
data class ChatMessage(val id: String, val senderId: String, val content: String, val time: String, val isMe: Boolean)

// For ProfileViewModel compatibility
data class PatientProfile(
    val id: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val bloodType: String = "",
    val address: String = "",
    val emergencyContact: String = "",
    val profileImageUrl: String? = null
)

// Summary models for UseCases and UI components
data class DoctorSummary(
    val id: String,
    val fullName: String,
    val specialty: String,
    val avgRating: Double,
    val totalReviews: Int = 0,
    val experienceYears: Int,
    val consultationFee: Double,
    val profileImage: String?,
    val isAvailable: Boolean,
    val bio: String = ""
)

data class AppointmentSummary(
    val id: String,
    val doctorName: String,
    val slotDate: String,
    val slotTime: String,
    val status: String,
    val type: String
)

data class ChatMessageModel(
    val id: String,
    val roomId: String,
    val senderId: String,
    val senderRole: String,
    val content: String,
    val sentAt: String,
    val isRead: Boolean
)

data class NotificationModel(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val isRead: Boolean,
    val sentAt: String
)

data class SlotModel(
    val id: String,
    val doctorId: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val status: String
)

data class SlotLockResult(
    val slotId: String,
    val locked: Boolean,
    val expiresAt: String,
    val ttlMinutes: Long
)

data class AiTriageReport(
    val id: String = "",
    val patientId: String = "",
    val urgencyScore: Int = 0,
    val suggestedSpecialty: String = "",
    val confidence: Double = 0.0,
    val recommendation: String = "",
    val riskFactors: List<String> = emptyList(),
    val createdAt: String = ""
)

