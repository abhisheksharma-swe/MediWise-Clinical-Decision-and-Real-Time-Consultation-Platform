package com.mediwise.domain.usecase.doctor

import com.mediwise.core.result.AppException
import com.mediwise.core.result.Result
import com.mediwise.domain.model.Doctor
import com.mediwise.domain.repository.DoctorRepository
import javax.inject.Inject

/** Mirrors [com.mediwise.domain.usecase.profile.UpdateProfileUseCase]'s pattern for the DOCTOR role. */
class UpdateMyDoctorProfileUseCase @Inject constructor(
    private val doctorRepository: DoctorRepository
) {
    suspend operator fun invoke(
        fullName: String,
        specialty: String,
        bio: String,
        experienceYears: String,
        consultationFee: String,
        available: Boolean
    ): Result<Doctor> {
        val trimmedName = fullName.trim()
        if (trimmedName.isBlank()) {
            return Result.Error(AppException.ValidationException("fullName", "Full name is required"))
        }

        val trimmedSpecialty = specialty.trim()
        if (trimmedSpecialty.isBlank()) {
            return Result.Error(AppException.ValidationException("specialty", "Specialty is required"))
        }

        val years = experienceYears.trim().let {
            if (it.isBlank()) 0 else it.toIntOrNull()
        }
        if (years == null || years < 0) {
            return Result.Error(AppException.ValidationException("experienceYears", "Experience must be a non-negative number of years"))
        }

        val fee = consultationFee.trim().toDoubleOrNull()
        if (fee == null || fee <= 0.0) {
            return Result.Error(AppException.ValidationException("consultationFee", "Consultation fee must be a number greater than zero — patients can't book without one"))
        }

        return doctorRepository.updateMyDoctorProfile(
            fullName = trimmedName,
            specialty = trimmedSpecialty,
            bio = bio.trim().ifBlank { null },
            experienceYears = years,
            consultationFee = fee,
            available = available
        )
    }
}
