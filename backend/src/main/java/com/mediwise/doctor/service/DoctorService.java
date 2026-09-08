package com.mediwise.doctor.service;

import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.doctor.dto.DoctorResponse;
import com.mediwise.doctor.dto.UpdateDoctorProfileRequest;
import com.mediwise.doctor.model.Doctor;
import com.mediwise.doctor.model.DoctorFavorite;
import com.mediwise.doctor.repository.DoctorFavoriteRepository;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.common.response.PagedResponse;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.repository.PatientProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DoctorFavoriteRepository favoriteRepository;
    private final PatientProfileRepository patientProfileRepository;

    @Cacheable(value = "doctor_list", key = "#search + '_' + #specialty + '_' + #sortBy + '_' + #page + '_' + #size")
    public PagedResponse<DoctorResponse> getDoctors(String search, String specialty,
                                           String sortBy, int page, int size) {
        Sort sort = resolveSort(sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Doctor> doctors;
        if (search != null && !search.isBlank()) {
            doctors = doctorRepository.search(search.trim(), pageable);
        } else if (specialty != null && !specialty.isBlank()) {
            Specification<Doctor> spec = Specification
                    .where(DoctorSpecification.isVerified())
                    .and(DoctorSpecification.hasSpecialty(specialty));
            doctors = doctorRepository.findAll(spec, pageable);
        } else {
            doctors = doctorRepository.findByAvailableTrueAndVerifiedTrue(pageable);
        }

        // Cached as a plain PagedResponse rather than Spring's Page/PageImpl —
        // PageImpl has no default constructor, so Jackson can serialize it into
        // Redis but can never deserialize it back out (see GlobalExceptionHandler
        // INTERNAL_ERROR logs referencing PageImpl deserialization failures).
        return PagedResponse.of(doctors.map(DoctorResponse::from));
    }

    public DoctorResponse getDoctorById(UUID id, User requester) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", id.toString()));

        if (!doctor.isVerified() && !canViewUnverifiedDoctor(doctor, requester)) {
            throw new ResourceNotFoundException("Doctor", id.toString());
        }

        return DoctorResponse.from(doctor);
    }

    // ── Doctor: view/update own professional profile ──────────────────────────
    public DoctorResponse getMyProfile(User user) {
        Doctor doctor = doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("DOCTOR_PROFILE_NOT_FOUND", "No doctor profile found for this account."));
        return DoctorResponse.from(doctor);
    }

    @CacheEvict(value = "doctor_list", allEntries = true)
    @Transactional
    public DoctorResponse updateMyProfile(User user, UpdateDoctorProfileRequest request) {
        Doctor doctor = doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("DOCTOR_PROFILE_NOT_FOUND", "No doctor profile found for this account."));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            doctor.setFullName(request.getFullName().trim());
        }
        if (request.getSpecialty() != null && !request.getSpecialty().isBlank()) {
            doctor.setSpecialty(request.getSpecialty().trim());
        }
        if (request.getBio() != null) {
            doctor.setBio(request.getBio());
        }
        if (request.getExperienceYears() != null) {
            doctor.setExperienceYears(request.getExperienceYears());
        }
        if (request.getConsultationFee() != null) {
            doctor.setConsultationFee(request.getConsultationFee());
        }
        if (request.getAvailable() != null) {
            doctor.setAvailable(request.getAvailable());
        }

        Doctor saved = doctorRepository.save(doctor);
        return DoctorResponse.from(saved);
    }

    private boolean canViewUnverifiedDoctor(Doctor doctor, User requester) {
        if (requester == null) {
            return false;
        }
        if (requester.getRole() == User.Role.ADMIN) {
            return true;
        }
        return requester.getRole() == User.Role.DOCTOR
                && doctor.getUserId().equals(requester.getId());
    }

    @CacheEvict(value = "doctor_list", allEntries = true)
    @Transactional
    public void toggleFavorite(User currentUser, UUID doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId.toString()));

        PatientProfile patient = patientProfileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new BusinessException("PROFILE_NOT_FOUND", "Patient profile not found"));

        if (favoriteRepository.existsByPatientIdAndDoctorId(patient.getId(), doctor.getId())) {
            favoriteRepository.deleteByPatientIdAndDoctorId(patient.getId(), doctor.getId());
        } else {
            favoriteRepository.save(DoctorFavorite.builder()
                    .patientId(patient.getId())
                    .doctorId(doctor.getId())
                    .build());
        }
    }

    public Page<DoctorResponse> getFavorites(User currentUser, int page, int size) {
        PatientProfile patient = patientProfileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new BusinessException("PROFILE_NOT_FOUND", "Patient profile not found"));

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return favoriteRepository.findByPatientId(patient.getId(), pageable)
                .map(fav -> {
                    Doctor doc = doctorRepository.findById(fav.getDoctorId()).orElseThrow();
                    return DoctorResponse.from(doc);
                });
    }

    private Sort resolveSort(String sortBy) {
        return switch (sortBy == null ? "rating" : sortBy) {
            case "fee_asc" -> Sort.by("consultationFee").ascending();
            case "fee_desc" -> Sort.by("consultationFee").descending();
            case "experience" -> Sort.by("experienceYears").descending();
            default -> Sort.by("avgRating").descending();
        };
    }
}