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
    val verified: Boolean = false,
    val clinicName: String? = null,
    val clinicAddress: String? = null,
    val consultationModes: List<String> = emptyList()
)

@Serializable
data class AppointmentDto(
    val id: String, val patientId: String, val doctorId: String,
    val patientUserId: String? = null, val doctorUserId: String? = null,
    val patientName: String? = null,
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
@Serializable data class CompleteAppointmentRequestDto(val notes: String, val diagnosis: String? = null, val prescription: String? = null)
@Serializable data class RescheduleAppointmentRequestDto(val newSlotId: String)

@Serializable
data class ConsultationDto(
    val id: String, val appointmentId: String, val patientId: String, val doctorId: String,
    val chiefComplaint: String? = null,
    val symptoms: List<String> = emptyList(),
    val observations: String? = null,
    val assessment: String? = null,
    val treatmentPlan: String? = null,
    val doctorNotes: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class UpdateConsultationRequestDto(
    val chiefComplaint: String? = null,
    val symptoms: List<String>? = null,
    val observations: String? = null,
    val assessment: String? = null,
    val treatmentPlan: String? = null,
    val notes: String? = null
)

@Serializable
data class PrescriptionItemRequestDto(
    val medicineName: String,
    val dosage: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
    val instructions: String? = null,
    val beforeAfterFood: String? = null
)

@Serializable
data class CreatePrescriptionRequestDto(
    val notes: String? = null,
    val items: List<PrescriptionItemRequestDto>
)

@Serializable
data class PrescriptionItemDto(
    val id: String? = null,
    val medicineName: String,
    val dosage: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
    val instructions: String? = null,
    val beforeAfterFood: String? = null,
    val sortOrder: Int = 0
)

@Serializable
data class PrescriptionDto(
    val id: String, val consultationId: String, val patientId: String, val doctorId: String,
    val notes: String? = null,
    val items: List<PrescriptionItemDto> = emptyList(),
    val createdAt: String? = null
)
@Serializable data class SubmitReviewRequestDto(val rating: Int, val reviewText: String? = null)

@Serializable
data class ConditionDto(
    val id: String, val name: String, val diagnosedDate: String? = null,
    val status: String? = null, val notes: String? = null, val selfReported: Boolean = false
)
@Serializable
data class AllergyDto(
    val id: String, val allergen: String, val reaction: String? = null,
    val severity: String? = null, val notes: String? = null, val selfReported: Boolean = false
)
@Serializable
data class MedicationDto(
    val id: String, val name: String, val dosage: String? = null, val frequency: String? = null,
    val startDate: String? = null, val endDate: String? = null, val active: Boolean = true, val selfReported: Boolean = false
)
@Serializable
data class MedicalRecordDto(
    val patientId: String,
    val conditions: List<ConditionDto> = emptyList(),
    val allergies: List<AllergyDto> = emptyList(),
    val medications: List<MedicationDto> = emptyList()
)
@Serializable data class CreateConditionRequestDto(val name: String, val diagnosedDate: String? = null, val notes: String? = null)
@Serializable data class CreateAllergyRequestDto(val allergen: String, val reaction: String? = null, val severity: String? = null, val notes: String? = null)
@Serializable data class CreateMedicationRequestDto(val name: String, val dosage: String? = null, val frequency: String? = null, val startDate: String? = null, val endDate: String? = null)
@Serializable data class UpdateConditionStatusRequestDto(val status: String)

@Serializable
data class CreateFollowUpRequestDto(val recommendedDate: String? = null, val reason: String? = null)

@Serializable
data class FollowUpDto(
    val id: String, val consultationId: String, val patientId: String, val doctorId: String,
    val recommendedDate: String? = null, val reason: String? = null, val status: String,
    val linkedAppointmentId: String? = null, val createdAt: String? = null
)

@Serializable
data class MedicalDocumentDto(
    val id: String, val patientId: String, val documentType: String,
    val originalFilename: String? = null, val contentType: String? = null, val sizeBytes: Long? = null,
    val relatedAppointmentId: String? = null, val relatedConsultationId: String? = null,
    val url: String? = null, val uploadedAt: String? = null
)
@Serializable
data class ReviewDto(
    val id: String, val appointmentId: String, val doctorId: String,
    val patientName: String? = null, val rating: Int, val reviewText: String? = null,
    val createdAt: String? = null
)

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
    val gatewayPaymentId: String? = null,
    val keyId: String? = null
)
@Serializable data class InitiatePaymentRequestDto(val appointmentId: String, val amount: String? = null)
@Serializable data class VerifyPaymentRequestDto(val razorpayOrderId: String, val razorpayPaymentId: String, val razorpaySignature: String)

@Serializable
data class UpdateDoctorProfileRequestDto(
    val fullName: String? = null,
    val specialty: String? = null,
    val bio: String? = null,
    val experienceYears: Int? = null,
    val consultationFee: Double? = null,
    val available: Boolean? = null
)

@Serializable data class ProfileDto(val id: String? = null, val userId: String? = null, val fullName: String? = null, val phone: String? = null, val dob: String? = null, val bloodType: String? = null, val gender: String? = null, val address: String? = null, val emergencyContact: String? = null, val profileImage: String? = null)
@Serializable data class UpdateProfileRequestDto(val fullName: String? = null, val dob: String? = null, val bloodType: String? = null, val gender: String? = null, val address: String? = null, val emergencyContact: String? = null)

@Serializable data class NotificationDto(val id: String, val title: String, val body: String? = null, val type: String, val read: Boolean = false, val sentAt: String? = null)

@Serializable
data class FcmTokenRequestDto(
    val fcmToken: String,
    val deviceId: String? = null,
    val platform: String? = "ANDROID",
    val appVersion: String? = null
)

@Serializable data class ChatMessageDto(val id: String? = null, val roomId: String, val senderId: String, val senderRole: String, val content: String, val contentType: String = "TEXT", val mediaUrl: String? = null, val sentAt: String? = null, val read: Boolean = false)

@Serializable data class ChatMediaUploadResponseDto(val url: String, val contentType: String, val originalFilename: String? = null)

/** STOMP /app/chat.send payload - mirrors the backend's SendMessageRequest{roomId,content,contentType,mediaUrl}. */
@Serializable data class StompSendMessageDto(val roomId: String, val content: String, val contentType: String = "TEXT", val mediaUrl: String? = null)

/** STOMP /app/chat.typing payload - mirrors the backend's TypingEvent{roomId,senderId,typing}. */
@Serializable data class StompTypingDto(val roomId: String, val senderId: String, val typing: Boolean)

/**
 * STOMP call payload and /user/queue/call broadcast shape - mirrors the backend's
 * CallSignalMessage{type,roomId,senderId,recipientId,payload}. `senderId` is set by the
 * server from the STOMP principal on every inbound signal, so the client only ever fills
 * it in on outbound sends as a formality (the server overwrites it regardless).
 */
@Serializable data class CallSignalDto(
    val type: String,
    val roomId: String,
    val senderId: String = "",
    val recipientId: String,
    val payload: String? = null
)

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
    val matchedDoctors: List<DoctorDto> = emptyList(),
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
    isAvailable = available,
    verified = verified,
    consultationModes = consultationModes.ifEmpty { listOf("ONLINE") }
)

fun AppointmentDto.toDomain() = com.mediwise.domain.model.Appointment(
    id = id,
    patientId = patientId,
    doctorId = doctorId,
    patientName = patientName ?: "",
    doctorName = doctorName ?: "",
    doctorSpecialty = doctorSpecialty ?: "",
    slotId = slotId,
    date = slotDate ?: "",
    time = if (slotStartTime != null && slotEndTime != null) "$slotStartTime - $slotEndTime" else (slotStartTime ?: ""),
    status = status,
    type = type,
    chiefComplaint = chiefComplaint ?: "",
    patientUserId = patientUserId ?: "",
    doctorUserId = doctorUserId ?: ""
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
fun ConsultationDto.toDomain() = com.mediwise.domain.model.Consultation(
    id = id,
    appointmentId = appointmentId,
    patientId = patientId,
    doctorId = doctorId,
    chiefComplaint = chiefComplaint ?: "",
    symptoms = symptoms,
    observations = observations ?: "",
    assessment = assessment ?: "",
    treatmentPlan = treatmentPlan ?: "",
    doctorNotes = doctorNotes ?: "",
    status = status ?: "",
    createdAt = createdAt ?: ""
)
fun PrescriptionItemDto.toDomain() = com.mediwise.domain.model.PrescriptionItemModel(
    id = id ?: "",
    medicineName = medicineName,
    dosage = dosage ?: "",
    frequency = frequency ?: "",
    duration = duration ?: "",
    instructions = instructions ?: "",
    beforeAfterFood = beforeAfterFood ?: ""
)
fun PrescriptionDto.toDomain() = com.mediwise.domain.model.PrescriptionModel(
    id = id,
    consultationId = consultationId,
    notes = notes ?: "",
    items = items.map { it.toDomain() },
    createdAt = createdAt ?: ""
)
fun ConditionDto.toDomain() = com.mediwise.domain.model.Condition(
    id = id, name = name, diagnosedDate = diagnosedDate ?: "",
    status = status ?: "ACTIVE", notes = notes ?: "", selfReported = selfReported
)
fun AllergyDto.toDomain() = com.mediwise.domain.model.Allergy(
    id = id, allergen = allergen, reaction = reaction ?: "",
    severity = severity ?: "", notes = notes ?: "", selfReported = selfReported
)
fun MedicationDto.toDomain() = com.mediwise.domain.model.Medication(
    id = id, name = name, dosage = dosage ?: "", frequency = frequency ?: "",
    startDate = startDate ?: "", endDate = endDate ?: "", active = active, selfReported = selfReported
)
fun MedicalRecordDto.toDomain() = com.mediwise.domain.model.MedicalRecord(
    patientId = patientId,
    conditions = conditions.map { it.toDomain() },
    allergies = allergies.map { it.toDomain() },
    medications = medications.map { it.toDomain() }
)
fun FollowUpDto.toDomain() = com.mediwise.domain.model.FollowUp(
    id = id, consultationId = consultationId, patientId = patientId, doctorId = doctorId,
    recommendedDate = recommendedDate ?: "", reason = reason ?: "", status = status,
    linkedAppointmentId = linkedAppointmentId ?: ""
)
fun MedicalDocumentDto.toDomain() = com.mediwise.domain.model.MedicalDocument(
    id = id, patientId = patientId, documentType = documentType,
    originalFilename = originalFilename ?: "", contentType = contentType ?: "", sizeBytes = sizeBytes ?: 0,
    url = url ?: "", uploadedAt = uploadedAt ?: ""
)
fun ReviewDto.toDomain() = com.mediwise.domain.model.Review(
    id = id,
    appointmentId = appointmentId,
    doctorId = doctorId,
    patientName = patientName ?: "",
    rating = rating,
    reviewText = reviewText ?: "",
    createdAt = createdAt ?: ""
)
fun ProfileDto.toDomain() = com.mediwise.domain.model.UserProfile(fullName = fullName ?: "", dob = dob ?: "", bloodType = bloodType ?: "", gender = gender ?: "", profileImage = profileImage)
fun ProfileDto.toPatientProfile(email: String = "", phone: String = "") = com.mediwise.domain.model.PatientProfile(
    id = id ?: "",
    fullName = fullName ?: "",
    email = email,
    phone = this.phone?.takeIf { it.isNotBlank() } ?: phone,
    dateOfBirth = dob ?: "",
    gender = gender ?: "",
    bloodType = bloodType ?: "",
    address = address ?: "",
    emergencyContact = emergencyContact ?: "",
    profileImageUrl = profileImage
)

fun AiReportDto.toDomain() = com.mediwise.domain.model.AiTriageReport(
    id = id ?: "",
    patientId = patientId ?: "",
    appointmentId = appointmentId,
    urgencyScore = urgencyScore,
    suggestedSpecialty = suggestedSpecialty ?: "",
    confidence = confidence,
    recommendation = recommendation ?: "",
    riskFactors = riskFactors,
    matchedDoctors = matchedDoctors.map { it.toDomain() },
    createdAt = createdAt ?: ""
)
fun NotificationDto.toDomain() = com.mediwise.domain.model.Notification(id = id, title = title, body = body ?: "", type = type, isRead = read, time = sentAt ?: "")
fun ChatMessageDto.toDomain() = com.mediwise.domain.model.ChatMessage(
    id = id ?: "",
    senderId = senderId,
    content = content,
    time = sentAt ?: "",
    isMe = false,
    contentType = com.mediwise.domain.model.ChatContentType.entries.firstOrNull { it.name == contentType } ?: com.mediwise.domain.model.ChatContentType.TEXT,
    mediaUrl = mediaUrl
)

