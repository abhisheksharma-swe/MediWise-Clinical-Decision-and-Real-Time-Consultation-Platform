package com.mediwise.domain.usecase.medicalrecord

import com.mediwise.core.result.Result
import com.mediwise.domain.model.MedicalDocument
import com.mediwise.domain.repository.MedicalDocumentRepository
import javax.inject.Inject

class GetMedicalDocumentsUseCase @Inject constructor(private val repo: MedicalDocumentRepository) {
    suspend operator fun invoke(patientId: String): Result<List<MedicalDocument>> = repo.listForPatient(patientId)
}
