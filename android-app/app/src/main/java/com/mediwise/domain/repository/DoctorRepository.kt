package com.mediwise.domain.repository

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Doctor

interface DoctorRepository {
    suspend fun getDoctors(specialty: String?, search: String?, sortBy: String = "rating", page: Int = 0, size: Int = 20): Result<List<Doctor>>
    suspend fun getDoctorById(id: String): Result<Doctor>
    suspend fun toggleFavorite(doctorId: String): Result<Unit>
    suspend fun getFavorites(page: Int = 0, size: Int = 20): Result<List<Doctor>>

    /** The logged-in DOCTOR user's own professional profile — never used for browsing other doctors. */
    suspend fun getMyDoctorProfile(): Result<Doctor>
    suspend fun updateMyDoctorProfile(
        fullName: String?,
        specialty: String?,
        bio: String?,
        experienceYears: Int?,
        consultationFee: Double?,
        available: Boolean?
    ): Result<Doctor>
}
