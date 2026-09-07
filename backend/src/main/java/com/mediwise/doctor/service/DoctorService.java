package com.mediwise.doctor.service;

import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.doctor.dto.DoctorResponse;
import com.mediwise.doctor.model.Doctor;
import com.mediwise.doctor.model.DoctorFavorite;
import com.mediwise.doctor.repository.DoctorFavoriteRepository;
import com.mediwise.doctor.repository.DoctorRepository;
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
    public Page<DoctorResponse> getDoctors(String search, String specialty,
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

        return doctors.map(DoctorResponse::from);
    }

    public DoctorResponse getDoctorById(UUID id, User requester) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", id.toString()));

        if (!doctor.isVerified() && !canViewUnverifiedDoctor(doctor, requester)) {
            throw new ResourceNotFoundException("Doctor", id.toString());
        }

        return DoctorResponse.from(doctor);
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