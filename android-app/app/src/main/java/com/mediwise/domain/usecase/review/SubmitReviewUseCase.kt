package com.mediwise.domain.usecase.review

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Review
import com.mediwise.domain.repository.ReviewRepository
import javax.inject.Inject

class SubmitReviewUseCase @Inject constructor(private val repo: ReviewRepository) {
    suspend operator fun invoke(appointmentId: String, rating: Int, reviewText: String?): Result<Review> =
        repo.submitReview(appointmentId, rating, reviewText)
}
