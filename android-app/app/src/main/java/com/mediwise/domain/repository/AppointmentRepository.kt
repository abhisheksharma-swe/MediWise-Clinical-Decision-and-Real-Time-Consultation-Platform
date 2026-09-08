package com.mediwise.domain.repository

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Appointment
import com.mediwise.domain.model.ConsultationRecord

interface AppointmentRepository {
    suspend fun getMyAppointments(status: String?, page: Int = 0, size: Int = 20): Result<List<Appointment>>
    suspend fun getAppointmentById(id: String): Result<Appointment>
    suspend fun bookAppointment(doctorId: String, slotId: String, type: String, chiefComplaint: String? = null): Result<Appointment>
    suspend fun cancelAppointment(id: String, reason: String? = null): Result<Appointment>
    suspend fun getDoctorAppointments(status: String?, page: Int = 0, size: Int = 20): Result<List<Appointment>>
        suspend fun getMyConsultationHistory(page: Int = 0, size: Int = 20): Result<List<ConsultationRecord>>
        suspend fun getPatientConsultationHistory(patientId: String, page: Int = 0, size: Int = 20): Result<List<ConsultationRecord>>
    suspend fun startAppointment(id: String): Result<Appointment>
    suspend fun completeAppointment(id: String, notes: String, diagnosis: String? = null, prescription: String? = null): Result<Appointment>
}
