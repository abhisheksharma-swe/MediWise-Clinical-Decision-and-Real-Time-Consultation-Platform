package com.mediwise.admin.service;

import com.mediwise.admin.dto.AdminStatsResponse;
import com.mediwise.admin.dto.UserSummaryResponse;
import com.mediwise.appointment.dto.AppointmentResponse;
import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.auth.model.User;
import com.mediwise.auth.repository.UserRepository;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.doctor.dto.DoctorResponse;
import com.mediwise.doctor.model.Doctor;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.payment.model.Payment;
import com.mediwise.payment.repository.PaymentRepository;
import com.mediwise.profile.repository.PatientProfileRepository;
import com.mediwise.schedule.model.TimeSlot;
import com.mediwise.schedule.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final PaymentRepository paymentRepository;
    private final TimeSlotRepository slotRepository;
    private final PatientProfileRepository patientProfileRepository;

    public Page<UserSummaryResponse> getUsers(User.Role role, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Specification<User> spec = Specification.where(null);

        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("phone")), pattern)));
        }

        return userRepository.findAll(spec, pageable).map(this::mapToUserSummary);
    }

    public UserSummaryResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));
        return mapToUserSummary(user);
    }

    @Transactional
    public UserSummaryResponse updateUserStatus(UUID id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));
        user.setActive(active);
        user = userRepository.save(user);
        log.info("User {} active status updated to {}", user.getEmail(), active);
        return mapToUserSummary(user);
    }

    @Transactional
    public UserSummaryResponse updateUserRole(UUID id, User.Role newRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));
        user.setRole(newRole);
        user = userRepository.save(user);
        log.info("User {} role updated to {} by administrator", user.getEmail(), newRole);
        return mapToUserSummary(user);
    }

    public Page<DoctorResponse> getDoctors(Boolean verified, String specialty, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Specification<Doctor> spec = Specification.where(null);

        if (verified != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("verified"), verified));
        }

        if (specialty != null && !specialty.isBlank()) {
            spec = spec.and(
                    (root, query, cb) -> cb.equal(cb.lower(root.get("specialty")), specialty.trim().toLowerCase()));
        }

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("specialty")), pattern),
                    cb.like(cb.lower(root.get("licenseNumber")), pattern)));
        }

        return doctorRepository.findAll(spec, pageable).map(DoctorResponse::from);
    }

    @Transactional
    public DoctorResponse verifyDoctor(UUID doctorId, boolean verified) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId.toString()));
        doctor.setVerified(verified);
        doctor = doctorRepository.save(doctor);

        // Also ensure user account is active
        userRepository.findById(doctor.getUserId()).ifPresent(user -> {
            user.setActive(true);
            userRepository.save(user);
        });

        log.info("Doctor {} verification status updated to {}", doctor.getFullName(), verified);
        return DoctorResponse.from(doctor);
    }

    @Transactional
    public void rejectDoctor(UUID doctorId, String reason) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId.toString()));
        doctor.setVerified(false);
        doctor.setAvailable(false);
        doctorRepository.save(doctor);

        // Deactivate associated user account
        userRepository.findById(doctor.getUserId()).ifPresent(user -> {
            user.setActive(false);
            userRepository.save(user);
        });

        log.info("Doctor {} rejected and deactivated. Reason: {}", doctor.getFullName(), reason);
    }

    /**
     * Aggregates platform statistics for admin dashboard.
     */
    public AdminStatsResponse getStats() {
        long totalUsers = userRepository.count();
        long totalPatients = userRepository.countByRole(User.Role.PATIENT);
        long totalDoctors = userRepository.countByRole(User.Role.DOCTOR);
        long activeUsers = userRepository.countByActiveTrue();
        long pendingVerifications = doctorRepository.countByVerifiedFalse();

        long totalAppointments = appointmentRepository.count();
        long pendingAppointments = appointmentRepository.countByStatus(Appointment.AppointmentStatus.PENDING);
        long confirmedAppointments = appointmentRepository.countByStatus(Appointment.AppointmentStatus.CONFIRMED);
        long completedAppointments = appointmentRepository.countByStatus(Appointment.AppointmentStatus.COMPLETED);
        long cancelledAppointments = appointmentRepository.countByStatus(Appointment.AppointmentStatus.CANCELLED);

        BigDecimal revenue = paymentRepository.sumSuccessfulPayments();
        if (revenue == null) {
            revenue = BigDecimal.ZERO;
        }
        long successfulPayments = paymentRepository.countByStatus(Payment.PaymentStatus.SUCCESS);

        return AdminStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalPatients(totalPatients)
                .totalDoctors(totalDoctors)
                .activeUsers(activeUsers)
                .pendingDoctorVerifications(pendingVerifications)
                .totalAppointments(totalAppointments)
                .pendingAppointments(pendingAppointments)
                .confirmedAppointments(confirmedAppointments)
                .completedAppointments(completedAppointments)
                .cancelledAppointments(cancelledAppointments)
                .totalRevenue(revenue)
                .successfulPayments(successfulPayments)
                .build();
    }

    /**
     * Admin: view all appointments across the entire platform with pagination &
     * filtering
     */
    public Page<AppointmentResponse> getAllAppointments(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        if (status != null && !status.isBlank()) {
            List<Appointment.AppointmentStatus> statuses = Arrays.stream(status.split(","))
                    .map(String::trim)
                    .map(s -> {
                        try {
                            return Appointment.AppointmentStatus.valueOf(s.toUpperCase());
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            if (!statuses.isEmpty()) {
                return appointmentRepository.findAll((root, query, cb) -> root.get("status").in(statuses), pageable)
                        .map(this::mapToAppointmentResponse);
            }
        }

        return appointmentRepository.findAll(pageable).map(this::mapToAppointmentResponse);
    }

    private UserSummaryResponse mapToUserSummary(User user) {
        return UserSummaryResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .dob(user.getDob())
                .role(user.getRole())
                .active(user.isActive())
                .firebaseUid(user.getFirebaseUid())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private AppointmentResponse mapToAppointmentResponse(Appointment appointment) {
        TimeSlot slot = slotRepository.findById(appointment.getSlotId()).orElse(null);
        Doctor doctor = doctorRepository.findById(appointment.getDoctorId()).orElse(null);
        String docName = doctor != null ? doctor.getFullName() : null;
        String docSpecialty = doctor != null ? doctor.getSpecialty() : null;
        String docImage = doctor != null ? doctor.getProfileImage() : null;
        UUID doctorUserId = doctor != null ? doctor.getUserId() : null;
        UUID patientUserId = patientProfileRepository.findById(appointment.getPatientId())
                .map(p -> p.getUserId()).orElse(null);
        String patientName = patientProfileRepository.findById(appointment.getPatientId())
            .map(com.mediwise.profile.model.PatientProfile::getFullName).orElse(null);
        return AppointmentResponse.from(appointment, slot, docName, docSpecialty, docImage, doctorUserId, patientUserId, patientName);
    }
}
