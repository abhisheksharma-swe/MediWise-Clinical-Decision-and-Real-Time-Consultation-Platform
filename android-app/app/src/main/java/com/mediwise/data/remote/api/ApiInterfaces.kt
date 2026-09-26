package com.mediwise.data.remote.api

import com.mediwise.data.remote.dto.*
import retrofit2.http.*

interface AuthApi {
    @GET("api/v1/auth/config")
    suspend fun getAppConfig(): ApiResponseDto<AppConfigDto>

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequestDto): ApiResponseDto<AuthResponseDto>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): ApiResponseDto<AuthResponseDto>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Header("X-Refresh-Token") refreshToken: String): ApiResponseDto<AuthResponseDto>

    @POST("api/v1/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequestDto): ApiResponseDto<Unit>

    @POST("api/v1/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequestDto): ApiResponseDto<Unit>

    @GET("api/v1/auth/me")
    suspend fun getCurrentUser(): ApiResponseDto<UserInfoDto>

    @POST("api/v1/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequestDto): ApiResponseDto<Unit>

    @POST("api/v1/auth/logout")
    suspend fun logout(@Header("Authorization") authHeader: String? = null): ApiResponseDto<Unit>
}

interface DoctorApi {
    @GET("api/v1/doctors")
    suspend fun getDoctors(
        @Query("search") search: String? = null,
        @Query("specialty") specialty: String? = null,
        @Query("sortBy") sortBy: String = "rating",
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10
    ): ApiResponseDto<PagedResponseDto<DoctorDto>>

    @GET("api/v1/doctors/me")
    suspend fun getMyDoctorProfile(): ApiResponseDto<DoctorDto>

    @PUT("api/v1/doctors/me")
    suspend fun updateMyDoctorProfile(@Body request: UpdateDoctorProfileRequestDto): ApiResponseDto<DoctorDto>

    @GET("api/v1/doctors/{id}")
    suspend fun getDoctorById(@Path("id") id: String): ApiResponseDto<DoctorDto>

    @POST("api/v1/doctors/{id}/favorite")
    suspend fun toggleFavorite(@Path("id") id: String): ApiResponseDto<Unit>

    @GET("api/v1/doctors/favorites")
    suspend fun getFavorites(@Query("page") page: Int = 0, @Query("size") size: Int = 10): ApiResponseDto<PagedResponseDto<DoctorDto>>
}

interface AppointmentApi {
    @POST("api/v1/appointments")
    suspend fun bookAppointment(@Body request: BookAppointmentRequestDto): ApiResponseDto<AppointmentDto>

    @GET("api/v1/appointments")
    suspend fun getMyAppointments(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10
    ): ApiResponseDto<PagedResponseDto<AppointmentDto>>
    @GET("api/v1/appointments/{id}")
    suspend fun getAppointmentById(@Path("id") id: String): ApiResponseDto<AppointmentDto>

    @PATCH("api/v1/appointments/{id}/cancel")
    suspend fun cancelAppointment(@Path("id") id: String, @Body request: CancelRequestDto): ApiResponseDto<AppointmentDto>

    @PATCH("api/v1/appointments/{id}/reschedule")
    suspend fun rescheduleAppointment(@Path("id") id: String, @Body request: RescheduleAppointmentRequestDto): ApiResponseDto<AppointmentDto>

    @GET("api/v1/appointments/doctor")
    suspend fun getDoctorAppointments(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10
    ): ApiResponseDto<PagedResponseDto<AppointmentDto>>

    @PATCH("api/v1/appointments/{id}/start")
    suspend fun startAppointment(@Path("id") id: String): ApiResponseDto<AppointmentDto>

    @PATCH("api/v1/appointments/{id}/complete")
    suspend fun completeAppointment(@Path("id") id: String, @Body request: CompleteAppointmentRequestDto): ApiResponseDto<AppointmentDto>
}

interface PaymentApi {
    @POST("api/v1/payments/initiate")
    suspend fun initiatePayment(@Body request: InitiatePaymentRequestDto): ApiResponseDto<PaymentDto>

    @POST("api/v1/payments/verify")
    suspend fun verifyPayment(@Body request: VerifyPaymentRequestDto): ApiResponseDto<PaymentDto>
}

interface ProfileApi {
    @GET("api/v1/profile")
    suspend fun getProfile(): ApiResponseDto<ProfileDto>

    @PUT("api/v1/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequestDto): ApiResponseDto<ProfileDto>

    @Multipart
    @POST("api/v1/profile/image")
    suspend fun uploadProfileImage(@Part file: okhttp3.MultipartBody.Part): ApiResponseDto<String>
}

interface NotificationApi {
    @GET("api/v1/notifications")
    suspend fun getNotifications(@Query("page") page: Int = 0, @Query("size") size: Int = 20): ApiResponseDto<PagedResponseDto<NotificationDto>>

    @PATCH("api/v1/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): ApiResponseDto<Unit>

    @PATCH("api/v1/notifications/read-all")
    suspend fun markAllRead(): ApiResponseDto<Unit>

    @POST("api/v1/notifications/fcm-token")
    suspend fun registerFcmToken(@Body request: FcmTokenRequestDto): ApiResponseDto<Unit>

    @HTTP(method = "DELETE", path = "api/v1/notifications/fcm-token", hasBody = true)
    suspend fun unregisterFcmToken(@Body request: FcmTokenRequestDto): ApiResponseDto<Unit>
}

interface ChatApi {
    @GET("api/v1/chat/{roomId}/messages")
    suspend fun getMessages(
        @Path("roomId") roomId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50
    ): ApiResponseDto<PagedResponseDto<ChatMessageDto>>

    @Multipart
    @POST("api/v1/chat/{roomId}/media")
    suspend fun uploadMedia(
        @Path("roomId") roomId: String,
        @Part file: okhttp3.MultipartBody.Part
    ): ApiResponseDto<ChatMediaUploadResponseDto>
}

interface SlotApi {
    @GET("api/v1/doctors/{doctorId}/slots")
    suspend fun getSlots(
        @Path("doctorId") doctorId: String,
        @Query("date") date: String
    ): ApiResponseDto<List<SlotDto>>

    @POST("api/v1/slots/{slotId}/lock")
    suspend fun lockSlot(@Path("slotId") slotId: String): ApiResponseDto<SlotLockResponseDto>

    @DELETE("api/v1/slots/{slotId}/lock")
    suspend fun releaseSlot(@Path("slotId") slotId: String): ApiResponseDto<Unit>
}

interface ConsultationApi {
    @GET("api/v1/consultations/{appointmentId}")
    suspend fun getForAppointment(@Path("appointmentId") appointmentId: String): ApiResponseDto<ConsultationDto?>

    @PUT("api/v1/consultations/{appointmentId}")
    suspend fun upsert(
        @Path("appointmentId") appointmentId: String,
        @Body request: UpdateConsultationRequestDto
    ): ApiResponseDto<ConsultationDto>

    @GET("api/v1/consultations/me")
    suspend fun getMyHistory(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): ApiResponseDto<PagedResponseDto<ConsultationDto>>

    @GET("api/v1/consultations/patient/{patientId}")
    suspend fun getPatientHistory(
        @Path("patientId") patientId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): ApiResponseDto<PagedResponseDto<ConsultationDto>>

    @POST("api/v1/consultations/{consultationId}/prescriptions")
    suspend fun createPrescription(
        @Path("consultationId") consultationId: String,
        @Body request: CreatePrescriptionRequestDto
    ): ApiResponseDto<PrescriptionDto>

    @GET("api/v1/prescriptions/me")
    suspend fun getMyPrescriptions(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): ApiResponseDto<PagedResponseDto<PrescriptionDto>>

    @GET("api/v1/prescriptions/patient/{patientId}")
    suspend fun getPrescriptionsForPatient(
        @Path("patientId") patientId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): ApiResponseDto<PagedResponseDto<PrescriptionDto>>
}

interface ReviewApi {
    @POST("api/v1/appointments/{id}/review")
    suspend fun submitReview(@Path("id") id: String, @Body request: SubmitReviewRequestDto): ApiResponseDto<ReviewDto>

    @GET("api/v1/doctors/{id}/reviews")
    suspend fun getReviewsForDoctor(
        @Path("id") id: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): ApiResponseDto<PagedResponseDto<ReviewDto>>
}

interface MedicalRecordApi {
    @GET("api/v1/medical-records/me")
    suspend fun getMyRecord(): ApiResponseDto<MedicalRecordDto>

    @POST("api/v1/medical-records/me/conditions")
    suspend fun addMyCondition(@Body request: CreateConditionRequestDto): ApiResponseDto<ConditionDto>

    @POST("api/v1/medical-records/me/allergies")
    suspend fun addMyAllergy(@Body request: CreateAllergyRequestDto): ApiResponseDto<AllergyDto>

    @POST("api/v1/medical-records/me/medications")
    suspend fun addMyMedication(@Body request: CreateMedicationRequestDto): ApiResponseDto<MedicationDto>

    @GET("api/v1/medical-records/{patientId}")
    suspend fun getRecord(@Path("patientId") patientId: String): ApiResponseDto<MedicalRecordDto>

    @POST("api/v1/medical-records/{patientId}/conditions")
    suspend fun addCondition(@Path("patientId") patientId: String, @Body request: CreateConditionRequestDto): ApiResponseDto<ConditionDto>

    @POST("api/v1/medical-records/{patientId}/allergies")
    suspend fun addAllergy(@Path("patientId") patientId: String, @Body request: CreateAllergyRequestDto): ApiResponseDto<AllergyDto>

    @POST("api/v1/medical-records/{patientId}/medications")
    suspend fun addMedication(@Path("patientId") patientId: String, @Body request: CreateMedicationRequestDto): ApiResponseDto<MedicationDto>

    @PATCH("api/v1/medical-records/conditions/{id}")
    suspend fun updateConditionStatus(@Path("id") id: String, @Body request: UpdateConditionStatusRequestDto): ApiResponseDto<ConditionDto>
}

interface FollowUpApi {
    @POST("api/v1/consultations/{consultationId}/follow-ups")
    suspend fun create(@Path("consultationId") consultationId: String, @Body request: CreateFollowUpRequestDto): ApiResponseDto<FollowUpDto>

    @GET("api/v1/follow-ups/me")
    suspend fun getMyFollowUps(): ApiResponseDto<List<FollowUpDto>>

    @GET("api/v1/follow-ups/patient/{patientId}")
    suspend fun getForPatient(@Path("patientId") patientId: String): ApiResponseDto<List<FollowUpDto>>

    @PATCH("api/v1/follow-ups/{id}/dismiss")
    suspend fun dismiss(@Path("id") id: String): ApiResponseDto<FollowUpDto>
}

interface MedicalDocumentApi {
    @Multipart
    @POST("api/v1/medical-documents")
    suspend fun upload(
        @Part("patientId") patientId: okhttp3.RequestBody,
        @Part("documentType") documentType: okhttp3.RequestBody,
        @Part file: okhttp3.MultipartBody.Part
    ): ApiResponseDto<MedicalDocumentDto>

    @GET("api/v1/medical-documents/patient/{patientId}")
    suspend fun listForPatient(@Path("patientId") patientId: String): ApiResponseDto<List<MedicalDocumentDto>>
}

interface AiApi {
    @POST("api/v1/ai/symptom-log")
    suspend fun logSymptoms(@Body request: SymptomLogRequestDto): ApiResponseDto<AiReportDto>

    @GET("api/v1/ai/reports/{patientId}/latest")
    suspend fun getLatestReport(@Path("patientId") patientId: String): ApiResponseDto<AiReportDto>

    @GET("api/v1/ai/reports/{patientId}")
    suspend fun getReportsForPatient(@Path("patientId") patientId: String): ApiResponseDto<List<AiReportDto>>
}

