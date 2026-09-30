package com.mediwise.domain.usecase.review

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Review
import com.mediwise.domain.repository.ReviewRepository
import javax.inject.Inject

class GetDoctorReviewsUseCase @Inject constructor(private val repo: ReviewRepository) {
    suspend operator fun invoke(doctorId: String, page: Int = 0, size: Int = 20): Result<List<Review>> =
        repo.getReviewsForDoctor(doctorId, page, size)
}
