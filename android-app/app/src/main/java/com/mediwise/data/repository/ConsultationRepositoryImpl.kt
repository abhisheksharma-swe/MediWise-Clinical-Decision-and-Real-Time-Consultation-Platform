package com.mediwise.data.repository

import com.mediwise.core.network.safeApiCall
import com.mediwise.core.result.Result
import com.mediwise.data.remote.api.ConsultationApi
import com.mediwise.data.remote.dto.CreatePrescriptionRequestDto
import com.mediwise.data.remote.dto.PrescriptionItemRequestDto
import com.mediwise.data.remote.dto.UpdateConsultationRequestDto
import com.mediwise.data.remote.dto.toDomain
import com.mediwise.domain.model.Consultation
import com.mediwise.domain.model.PrescriptionItemModel
import com.mediwise.domain.model.PrescriptionModel
import com.mediwise.domain.repository.ConsultationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsultationRepositoryImpl @Inject constructor(
    private val api: ConsultationApi
) : ConsultationRepository {

    override suspend fun getForAppointment(appointmentId: String): Result<Consultation?> {
        return safeApiCall {
            api.getForAppointment(appointmentId).data?.toDomain()
        }
    }

    override suspend fun upsert(
        appointmentId: String,
        chiefComplaint: String?,
        symptoms: List<String>?,
        observations: String?,
        assessment: String?,
        treatmentPlan: String?,
        notes: String?
    ): Result<Consultation> {
        return safeApiCall {
            val request = UpdateConsultationRequestDto(
                chiefComplaint = chiefComplaint,
                symptoms = symptoms,
                observations = observations,
                assessment = assessment,
                treatmentPlan = treatmentPlan,
                notes = notes
            )
            val response = api.upsert(appointmentId, request)
            response.data?.toDomain() ?: throw Exception("Empty consultation response")
        }
    }

    override suspend fun getPatientHistory(patientId: String, page: Int, size: Int): Result<List<Consultation>> {
        return safeApiCall {
            api.getPatientHistory(patientId, page, size).data?.content?.map { it.toDomain() } ?: emptyList()
        }
    }

    override suspend fun getMyHistory(page: Int, size: Int): Result<List<Consultation>> {
        return safeApiCall {
            api.getMyHistory(page, size).data?.content?.map { it.toDomain() } ?: emptyList()
        }
    }

    override suspend fun createPrescription(
        consultationId: String,
        notes: String?,
        items: List<PrescriptionItemModel>
    ): Result<PrescriptionModel> {
        return safeApiCall {
            val request = CreatePrescriptionRequestDto(
                notes = notes,
                items = items.map {
                    PrescriptionItemRequestDto(
                        medicineName = it.medicineName,
                        dosage = it.dosage.ifBlank { null },
                        frequency = it.frequency.ifBlank { null },
                        duration = it.duration.ifBlank { null },
                        instructions = it.instructions.ifBlank { null },
                        beforeAfterFood = it.beforeAfterFood.ifBlank { null }
                    )
                }
            )
            val response = api.createPrescription(consultationId, request)
            response.data?.toDomain() ?: throw Exception("Empty prescription response")
        }
    }

    override suspend fun getPrescriptionsForPatient(patientId: String, page: Int, size: Int): Result<List<PrescriptionModel>> {
        return safeApiCall {
            api.getPrescriptionsForPatient(patientId, page, size).data?.content?.map { it.toDomain() } ?: emptyList()
        }
    }

    override suspend fun getMyPrescriptions(page: Int, size: Int): Result<List<PrescriptionModel>> {
        return safeApiCall {
            api.getMyPrescriptions(page, size).data?.content?.map { it.toDomain() } ?: emptyList()
        }
    }
}
