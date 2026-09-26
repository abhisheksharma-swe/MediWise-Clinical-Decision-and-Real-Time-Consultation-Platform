package com.mediwise.domain.repository

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Review

interface ReviewRepository {
    suspend fun submitReview(appointmentId: String, rating: Int, reviewText: String?): Result<Review>
    suspend fun getReviewsForDoctor(doctorId: String, page: Int = 0, size: Int = 20): Result<List<Review>>
}
