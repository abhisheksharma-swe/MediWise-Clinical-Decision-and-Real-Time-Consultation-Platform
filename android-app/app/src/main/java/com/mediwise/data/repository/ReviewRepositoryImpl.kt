package com.mediwise.data.repository

import com.mediwise.core.network.safeApiCall
import com.mediwise.core.result.Result
import com.mediwise.data.remote.api.ReviewApi
import com.mediwise.data.remote.dto.SubmitReviewRequestDto
import com.mediwise.data.remote.dto.toDomain
import com.mediwise.domain.model.Review
import com.mediwise.domain.repository.ReviewRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewRepositoryImpl @Inject constructor(
    private val api: ReviewApi
) : ReviewRepository {

    override suspend fun submitReview(appointmentId: String, rating: Int, reviewText: String?): Result<Review> {
        return safeApiCall {
            val response = api.submitReview(appointmentId, SubmitReviewRequestDto(rating = rating, reviewText = reviewText))
            val data = response.data ?: throw Exception("Empty review response")
            data.toDomain()
        }
    }

    override suspend fun getReviewsForDoctor(doctorId: String, page: Int, size: Int): Result<List<Review>> {
        return safeApiCall {
            val response = api.getReviewsForDoctor(doctorId, page, size)
            response.data?.content?.map { it.toDomain() } ?: emptyList()
        }
    }
}
