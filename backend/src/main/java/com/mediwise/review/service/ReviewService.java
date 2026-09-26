package com.mediwise.review.service;

import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.doctor.model.Doctor;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.repository.PatientProfileRepository;
import com.mediwise.review.dto.ReviewResponse;
import com.mediwise.review.dto.SubmitReviewRequest;
import com.mediwise.review.model.DoctorRating;
import com.mediwise.review.repository.DoctorRatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final DoctorRatingRepository ratingRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientProfileRepository patientProfileRepository;

    @Transactional
    public ReviewResponse submit(UUID appointmentId, User user, SubmitReviewRequest request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId.toString()));

        PatientProfile patient = patientProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("FORBIDDEN", "No patient profile for this user."));
        if (!appointment.getPatientId().equals(patient.getId())) {
            throw new BusinessException("FORBIDDEN", "You can only review your own appointments.");
        }
        if (appointment.getStatus() != Appointment.AppointmentStatus.COMPLETED) {
            throw new BusinessException("INVALID_STATE", "Only completed appointments can be reviewed.");
        }
        if (ratingRepository.existsByAppointmentId(appointmentId)) {
            throw new BusinessException("ALREADY_REVIEWED", "This appointment has already been reviewed.");
        }

        DoctorRating rating = DoctorRating.builder()
                .appointmentId(appointmentId)
                .patientId(appointment.getPatientId())
                .doctorId(appointment.getDoctorId())
                .rating(request.getRating().shortValue())
                .review(request.getReviewText())
                .build();
        rating = ratingRepository.save(rating);

        recomputeDoctorRating(appointment.getDoctorId());

        return ReviewResponse.from(rating, patient.getFullName());
    }

    public Page<ReviewResponse> getForDoctor(UUID doctorId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return ratingRepository.findByDoctorIdOrderByCreatedAtDesc(doctorId, pageable)
                .map(r -> ReviewResponse.from(r, patientProfileRepository.findById(r.getPatientId())
                        .map(PatientProfile::getFullName).orElse(null)));
    }

    private void recomputeDoctorRating(UUID doctorId) {
        Double avg = ratingRepository.averageRatingForDoctor(doctorId);
        long count = ratingRepository.countByDoctorId(doctorId);
        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow();
        doctor.setAvgRating(avg != null ? BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        doctor.setTotalReviews((int) count);
        doctorRepository.save(doctor);
    }
}
