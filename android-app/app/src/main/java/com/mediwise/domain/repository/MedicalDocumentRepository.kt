package com.mediwise.domain.repository

import com.mediwise.core.result.Result
import com.mediwise.domain.model.MedicalDocument

interface MedicalDocumentRepository {
    suspend fun upload(patientId: String, documentType: String, fileBytes: ByteArray, fileName: String, mimeType: String): Result<MedicalDocument>
    suspend fun listForPatient(patientId: String): Result<List<MedicalDocument>>
}
