package com.mediwise.domain.model

/**
 * The authenticated user's role, parsed from the raw role string the backend/session
 * store hands back. Kept separate from [User.role] (still a raw `String`, matching the
 * wire format used at registration/login) — this enum exists purely to replace ad-hoc
 * `role == "DOCTOR"` string comparisons scattered across the UI layer with a type-safe
 * equivalent.
 */
enum class Role {
    PATIENT, DOCTOR, ADMIN;

    companion object {
        /** Null for a not-yet-loaded session or an unrecognized role string — never guesses. */
        fun fromRaw(raw: String?): Role? = raw?.let { r -> entries.firstOrNull { it.name == r } }
    }
}

data class User(val id: String, val email: String, val role: String)
data class AuthResult(val accessToken: String, val refreshToken: String, val user: User)
data class Doctor(val id: String, val fullName: String, val specialty: String, val consultationFee: String, val rating: Double, val reviewCount: Int, val profileImage: String?, val available: Boolean, val bio: String = "", val avgRating: Double = 0.0, val totalReviews: Int = 0, val experienceYears: Int = 0, val isAvailable: Boolean = true, val email: String = "", val verified: Boolean = false)
data class Appointment(
    val id: String, val patientId: String, val doctorId: String,
    val patientName: String = "", val doctorName: String = "", val doctorSpecialty: String = "", val slotId: String,
    val date: String, val time: String, val status: String, val type: String, val chiefComplaint: String = "",
    /** The User ids behind patientId/doctorId - needed to address WebRTC call signaling (routed by user id, not profile id). Blank if the backend didn't provide them (e.g. an older cached response). */
    val patientUserId: String = "", val doctorUserId: String = ""
)
data class ConsultationRecord(
    val id: String,
    val patientId: String,
    val doctorName: String,
    val doctorSpecialty: String,
    val date: String,
    val chiefComplaint: String,
    val notes: String,
    val diagnosis: String,
    val prescription: String
)
data class Payment(val id: String, val appointmentId: String, val amount: String, val status: String)

data class PaymentOrder(
    val paymentId: String,
    val appointmentId: String,
    val gatewayOrderId: String,
    val amount: Double,
    val currency: String,
    val keyId: String
)
data class UserProfile(val fullName: String, val dob: String, val bloodType: String, val gender: String, val profileImage: String?)
data class Notification(val id: String, val title: String, val body: String, val type: String, val isRead: Boolean, val time: String)
enum class ChatContentType { TEXT, IMAGE, FILE }

data class ChatMessage(
    val id: String, val senderId: String, val content: String, val time: String, val isMe: Boolean,
    val contentType: ChatContentType = ChatContentType.TEXT,
    val mediaUrl: String? = null
)

enum class ConnectionStatus { CONNECTING, CONNECTED, DISCONNECTED, ERROR }

data class RealtimeNotification(val id: String, val type: String, val title: String, val body: String, val refId: String?)

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
    val appointmentId: String? = null,
    val urgencyScore: Int = 0,
    val suggestedSpecialty: String = "",
    val confidence: Double = 0.0,
    val recommendation: String = "",
    val riskFactors: List<String> = emptyList(),
    val matchedDoctors: List<Doctor> = emptyList(),
    val createdAt: String = ""
)

