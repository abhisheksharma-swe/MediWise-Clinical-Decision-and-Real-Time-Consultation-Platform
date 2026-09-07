package com.mediwise.data.repository

import com.mediwise.core.network.safeApiCall
import com.mediwise.core.result.Result
import com.mediwise.data.remote.api.AiApi
import com.mediwise.data.remote.dto.SymptomLogRequestDto
import com.mediwise.data.remote.dto.toDomain
import com.mediwise.domain.model.AiTriageReport
import com.mediwise.domain.repository.AiRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val api: AiApi
) : AiRepository {

    override suspend fun logSymptoms(request: SymptomLogRequestDto): Result<AiTriageReport> {
        return safeApiCall {
            val response = api.logSymptoms(request)
            val data = response.data ?: throw Exception("Empty AI report response")
            data.toDomain()
        }
    }

    override suspend fun getLatestReport(patientId: String): Result<AiTriageReport> {
        return safeApiCall {
            val response = api.getLatestReport(patientId)
            val data = response.data ?: throw Exception("No AI report found")
            data.toDomain()
        }
    }

    override suspend fun getReportsForPatient(patientId: String): Result<List<AiTriageReport>> {
        return safeApiCall {
            val response = api.getReportsForPatient(patientId)
            val data = response.data ?: emptyList()
            data.map { it.toDomain() }
        }
    }
}
