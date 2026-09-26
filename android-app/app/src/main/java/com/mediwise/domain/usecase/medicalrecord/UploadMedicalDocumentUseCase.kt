package com.mediwise.domain.usecase.medicalrecord

import com.mediwise.core.result.Result
import com.mediwise.domain.model.MedicalDocument
import com.mediwise.domain.repository.MedicalDocumentRepository
import javax.inject.Inject

class UploadMedicalDocumentUseCase @Inject constructor(private val repo: MedicalDocumentRepository) {
    suspend operator fun invoke(patientId: String, documentType: String, fileBytes: ByteArray, fileName: String, mimeType: String): Result<MedicalDocument> =
        repo.upload(patientId, documentType, fileBytes, fileName, mimeType)
}
