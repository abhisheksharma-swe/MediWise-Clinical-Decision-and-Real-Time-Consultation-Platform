package com.mediwise.domain.usecase.followup

import com.mediwise.core.result.Result
import com.mediwise.domain.model.FollowUp
import com.mediwise.domain.repository.FollowUpRepository
import javax.inject.Inject

class CreateFollowUpUseCase @Inject constructor(private val repo: FollowUpRepository) {
    suspend operator fun invoke(consultationId: String, recommendedDate: String?, reason: String?): Result<FollowUp> =
        repo.create(consultationId, recommendedDate, reason)
}

class GetMyFollowUpsUseCase @Inject constructor(private val repo: FollowUpRepository) {
    suspend operator fun invoke(): Result<List<FollowUp>> = repo.getMyFollowUps()
}

class GetPatientFollowUpsUseCase @Inject constructor(private val repo: FollowUpRepository) {
    suspend operator fun invoke(patientId: String): Result<List<FollowUp>> = repo.getForPatient(patientId)
}

class DismissFollowUpUseCase @Inject constructor(private val repo: FollowUpRepository) {
    suspend operator fun invoke(id: String): Result<FollowUp> = repo.dismiss(id)
}
