package com.mediwise.domain.usecase.doctor

import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.core.result.Result
import com.mediwise.domain.model.Doctor
import com.mediwise.domain.repository.DoctorRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/** Mirrors [com.mediwise.domain.usecase.profile.GetProfileUseCase]'s pattern for the DOCTOR role. */
class GetMyDoctorProfileUseCase @Inject constructor(
    private val doctorRepository: DoctorRepository,
    private val sessionDataStore: SessionDataStore
) {
    suspend operator fun invoke(): Result<Doctor> {
        val result = doctorRepository.getMyDoctorProfile()
        return when (result) {
            is Result.Success -> {
                val email = sessionDataStore.userEmail.firstOrNull() ?: ""
                val doctor = if (result.data.email.isBlank() && email.isNotBlank()) {
                    result.data.copy(email = email)
                } else {
                    result.data
                }
                Result.Success(doctor)
            }
            is Result.Error -> result
            is Result.Loading -> Result.Loading
        }
    }
}
