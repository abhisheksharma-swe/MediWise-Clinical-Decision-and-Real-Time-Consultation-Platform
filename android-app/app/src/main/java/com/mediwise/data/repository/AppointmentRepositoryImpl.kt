package com.mediwise.data.repository

import com.mediwise.core.network.safeApiCall
import com.mediwise.core.result.Result
import com.mediwise.data.local.dao.AppointmentDao
import com.mediwise.data.local.entity.toEntity
import com.mediwise.data.remote.api.AppointmentApi
import com.mediwise.data.remote.dto.*
import com.mediwise.domain.model.Appointment
import com.mediwise.domain.repository.AppointmentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppointmentRepositoryImpl @Inject constructor(
    private val api: AppointmentApi,
    private val appointmentDao: AppointmentDao
) : AppointmentRepository {
    
    override suspend fun getMyAppointments(status: String?, page: Int, size: Int): Result<List<Appointment>> {
        val result = safeApiCall {
            val response = api.getMyAppointments(status, page, size)
            response.data?.content?.map { it.toDomain() } ?: emptyList()
        }
        
        if (result is Result.Success) {
            appointmentDao.insertAppointments(result.data.map { it.toEntity() })
        }
        return result
    }

    override suspend fun getAppointmentById(id: String): Result<Appointment> {
        // Always refresh details from the server. The API includes participant user IDs
        // required for call signaling, while the legacy Room cache does not contain them.
        val remoteResult = safeApiCall {
            val response = api.getAppointmentById(id)
            val appt = response.data!!.toDomain()
            appointmentDao.insertAppointment(appt.toEntity())
            appt
        }
        if (remoteResult is Result.Success) return remoteResult

        // Preserve offline appointment-detail behavior when the server cannot be reached.
        return appointmentDao.getAppointmentById(id)?.let { Result.Success(it.toDomain()) }
            ?: remoteResult
    }

    override suspend fun bookAppointment(doctorId: String, slotId: String, type: String, chiefComplaint: String?): Result<Appointment> {
        return safeApiCall {
            val request = BookAppointmentRequestDto(slotId = slotId, doctorId = doctorId, type = type, chiefComplaint = chiefComplaint)
            val response = api.bookAppointment(request)
            val newAppt = response.data!!.toDomain()
            appointmentDao.insertAppointment(newAppt.toEntity())
            newAppt
        }
    }

    override suspend fun cancelAppointment(id: String, reason: String?): Result<Appointment> {
        return safeApiCall {
            val request = CancelRequestDto(reason = reason)
            val response = api.cancelAppointment(id, request)
            val cancelledAppt = response.data!!.toDomain()
            appointmentDao.insertAppointment(cancelledAppt.toEntity())
            cancelledAppt
        }
    }

    override suspend fun getDoctorAppointments(status: String?, page: Int, size: Int): Result<List<Appointment>> {
        val result = safeApiCall {
            val response = api.getDoctorAppointments(status, page, size)
            response.data?.content?.map { it.toDomain() } ?: emptyList()
        }

        if (result is Result.Success) {
            appointmentDao.insertAppointments(result.data.map { it.toEntity() })
        }
        return result
    }

    override suspend fun getMyConsultationHistory(page: Int, size: Int): Result<List<com.mediwise.domain.model.ConsultationRecord>> =
        safeApiCall {
            api.getMyConsultationHistory(page, size).data?.content?.map { it.toConsultationRecord() } ?: emptyList()
        }

    override suspend fun getPatientConsultationHistory(patientId: String, page: Int, size: Int): Result<List<com.mediwise.domain.model.ConsultationRecord>> =
        safeApiCall {
            api.getPatientConsultationHistory(patientId, page, size).data?.content?.map { it.toConsultationRecord() } ?: emptyList()
        }

    override suspend fun startAppointment(id: String): Result<Appointment> {
        return safeApiCall {
            val response = api.startAppointment(id)
            val startedAppt = response.data!!.toDomain()
            appointmentDao.insertAppointment(startedAppt.toEntity())
            startedAppt
        }
    }

    override suspend fun completeAppointment(id: String, notes: String, diagnosis: String?, prescription: String?): Result<Appointment> {
        return safeApiCall {
            val request = CompleteAppointmentRequestDto(notes = notes, diagnosis = diagnosis, prescription = prescription)
            val response = api.completeAppointment(id, request)
            val completedAppt = response.data!!.toDomain()
            appointmentDao.insertAppointment(completedAppt.toEntity())
            completedAppt
        }
    }
}
