package com.mediwise.domain.repository

import com.mediwise.core.result.Result
import com.mediwise.data.remote.dto.SymptomLogRequestDto
import com.mediwise.domain.model.AiTriageReport

interface AiRepository {
    suspend fun logSymptoms(request: SymptomLogRequestDto): Result<AiTriageReport>
    suspend fun getLatestReport(patientId: String): Result<AiTriageReport>
    suspend fun getReportsForPatient(patientId: String): Result<List<AiTriageReport>>
}
