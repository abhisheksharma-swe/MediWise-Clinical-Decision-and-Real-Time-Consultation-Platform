package com.mediwise.data.repository

import com.mediwise.core.network.safeApiCall
import com.mediwise.core.result.Result
import com.mediwise.data.remote.api.FollowUpApi
import com.mediwise.data.remote.dto.CreateFollowUpRequestDto
import com.mediwise.data.remote.dto.toDomain
import com.mediwise.domain.model.FollowUp
import com.mediwise.domain.repository.FollowUpRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FollowUpRepositoryImpl @Inject constructor(
    private val api: FollowUpApi
) : FollowUpRepository {

    override suspend fun create(consultationId: String, recommendedDate: String?, reason: String?): Result<FollowUp> {
        return safeApiCall {
            val response = api.create(consultationId, CreateFollowUpRequestDto(recommendedDate, reason))
            response.data?.toDomain() ?: throw Exception("Empty follow-up response")
        }
    }

    override suspend fun getMyFollowUps(): Result<List<FollowUp>> {
        return safeApiCall {
            api.getMyFollowUps().data?.map { it.toDomain() } ?: emptyList()
        }
    }

    override suspend fun getForPatient(patientId: String): Result<List<FollowUp>> {
        return safeApiCall {
            api.getForPatient(patientId).data?.map { it.toDomain() } ?: emptyList()
        }
    }

    override suspend fun dismiss(id: String): Result<FollowUp> {
        return safeApiCall {
            api.dismiss(id).data?.toDomain() ?: throw Exception("Empty follow-up response")
        }
    }
}
