package com.mediwise.data.repository

import com.mediwise.core.network.safeApiCall
import com.mediwise.core.result.Result
import com.mediwise.data.local.dao.DoctorDao
import com.mediwise.data.local.entity.toEntity
import com.mediwise.data.remote.api.DoctorApi
import com.mediwise.data.remote.dto.*
import com.mediwise.domain.model.Doctor
import com.mediwise.domain.repository.DoctorRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorRepositoryImpl @Inject constructor(
    private val api: DoctorApi,
    private val doctorDao: DoctorDao
) : DoctorRepository {
    override suspend fun getDoctors(specialty: String?, search: String?, sortBy: String, page: Int, size: Int): Result<List<Doctor>> {
        val result = safeApiCall {
            val response = api.getDoctors(
                specialty = if (specialty == "All") null else specialty,
                search = search?.takeIf { it.isNotBlank() },
                sortBy = sortBy,
                page = page,
                size = size
            )
            response.data?.content?.map { it.toDomain() } ?: emptyList()
        }
        
        if (result is Result.Success) {
            doctorDao.insertDoctors(result.data.map { it.toEntity() })
        }
        
        return result
    }

    override suspend fun getDoctorById(id: String): Result<Doctor> {
        val cached = doctorDao.getDoctorById(id)
        if (cached != null) {
            return Result.Success(cached.toDomain())
        }
        
        return safeApiCall {
            val response = api.getDoctorById(id)
            val doctor = response.data!!.toDomain()
            doctorDao.insertDoctors(listOf(doctor.toEntity()))
            doctor
        }
    }

    override suspend fun toggleFavorite(doctorId: String): Result<Unit> {
        return safeApiCall {
            api.toggleFavorite(doctorId)
            Unit
        }
    }

    override suspend fun getFavorites(page: Int, size: Int): Result<List<Doctor>> {
        return safeApiCall {
            val response = api.getFavorites(page, size)
            response.data?.content?.map { it.toDomain() } ?: emptyList()
        }
    }

    override suspend fun getMyDoctorProfile(): Result<Doctor> {
        return safeApiCall {
            val response = api.getMyDoctorProfile()
            response.data!!.toDomain()
        }
    }

    override suspend fun updateMyDoctorProfile(
        fullName: String?,
        specialty: String?,
        bio: String?,
        experienceYears: Int?,
        consultationFee: Double?,
        available: Boolean?
    ): Result<Doctor> {
        return safeApiCall {
            val response = api.updateMyDoctorProfile(
                UpdateDoctorProfileRequestDto(
                    fullName = fullName,
                    specialty = specialty,
                    bio = bio,
                    experienceYears = experienceYears,
                    consultationFee = consultationFee,
                    available = available
                )
            )
            response.data!!.toDomain()
        }
    }
}
