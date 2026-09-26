package com.mediwise.domain.repository

import com.mediwise.core.result.Result
import com.mediwise.domain.model.FollowUp

interface FollowUpRepository {
    suspend fun create(consultationId: String, recommendedDate: String?, reason: String?): Result<FollowUp>
    suspend fun getMyFollowUps(): Result<List<FollowUp>>
    suspend fun getForPatient(patientId: String): Result<List<FollowUp>>
    suspend fun dismiss(id: String): Result<FollowUp>
}
