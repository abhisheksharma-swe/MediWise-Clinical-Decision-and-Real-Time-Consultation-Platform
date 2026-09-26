package com.mediwise.domain.repository

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Consultation
import com.mediwise.domain.model.PrescriptionItemModel
import com.mediwise.domain.model.PrescriptionModel

interface ConsultationRepository {
    suspend fun getForAppointment(appointmentId: String): Result<Consultation?>
    suspend fun upsert(
        appointmentId: String,
        chiefComplaint: String?,
        symptoms: List<String>?,
        observations: String?,
        assessment: String?,
        treatmentPlan: String?,
        notes: String?
    ): Result<Consultation>
    suspend fun getPatientHistory(patientId: String, page: Int = 0, size: Int = 20): Result<List<Consultation>>
    suspend fun getMyHistory(page: Int = 0, size: Int = 20): Result<List<Consultation>>
    suspend fun createPrescription(consultationId: String, notes: String?, items: List<PrescriptionItemModel>): Result<PrescriptionModel>
    suspend fun getPrescriptionsForPatient(patientId: String, page: Int = 0, size: Int = 20): Result<List<PrescriptionModel>>
    suspend fun getMyPrescriptions(page: Int = 0, size: Int = 20): Result<List<PrescriptionModel>>
}
