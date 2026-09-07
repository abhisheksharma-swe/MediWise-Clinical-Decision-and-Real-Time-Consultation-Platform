package com.mediwise.appointment.service;

import com.mediwise.appointment.dto.AppointmentResponse;
import com.mediwise.appointment.dto.BookAppointmentRequest;
import com.mediwise.appointment.dto.CancelRequest;
import com.mediwise.appointment.dto.CompleteAppointmentRequest;
import com.mediwise.appointment.event.AppointmentBookedEvent;
import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.common.exception.SlotConflictException;
import com.mediwise.doctor.model.Doctor;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.repository.PatientProfileRepository;
import com.mediwise.schedule.model.TimeSlot;
import com.mediwise.schedule.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final TimeSlotRepository slotRepository;
    private final DoctorRepository doctorRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ── Patient: Book appointment after slot lock + payment ───────────────────
    @Transactional
    @CacheEvict(value = "slots", allEntries = true)
    public AppointmentResponse bookAppointment(User user, BookAppointmentRequest request) {
        PatientProfile patient = patientProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    PatientProfile newProfile = PatientProfile.builder()
                            .userId(user.getId())
                            .fullName(user.getFullName() != null ? user.getFullName() : "Patient")
                            .dob(user.getDob())
                            .build();
                    return patientProfileRepository.save(newProfile);
                });

        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", request.getDoctorId().toString()));

        if (!doctor.isVerified()) {
            throw new BusinessException("DOCTOR_NOT_VERIFIED", "This doctor is not yet verified and cannot be booked.");
        }

        TimeSlot slot = slotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot", request.getSlotId().toString()));

        // The slot must be locked by THIS user — prevents race condition
        if (slot.getStatus() != TimeSlot.SlotStatus.LOCKED ||
                !user.getId().equals(slot.getLockedBy())) {
            throw new SlotConflictException("Slot is not reserved for you. Please re-select.");
        }

        // Mark slot as permanently BOOKED
        slot.setStatus(TimeSlot.SlotStatus.BOOKED);
        slot.setLockedBy(null);
        slot.setLockedUntil(null);
        slotRepository.save(slot);

        Appointment appointment = Appointment.builder()
                .patientId(patient.getId())
                .doctorId(request.getDoctorId())
                .slotId(slot.getId())
                .type(request.getType())
                .chiefComplaint(request.getChiefComplaint())
                .status(Appointment.AppointmentStatus.PENDING)
                .build();

        appointment = appointmentRepository.save(appointment);
        log.info("Appointment {} booked by patient {}", appointment.getId(), patient.getId());

        // Publish domain event → triggers async push notification to doctor
        eventPublisher.publishEvent(new AppointmentBookedEvent(this, appointment));

        return buildAppointmentResponse(appointment, slot);
    }

    // ── Patient: List their own appointments ──────────────────────────────────
    public Page<AppointmentResponse> getMyAppointments(User user, String status, int page, int size) {
        PatientProfile patient = patientProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    PatientProfile newProfile = PatientProfile.builder()
                            .userId(user.getId())
                            .fullName(user.getFullName() != null ? user.getFullName() : "Patient")
                            .dob(user.getDob())
                            .build();
                    return patientProfileRepository.save(newProfile);
                });

        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        if (status != null && !status.isBlank()) {
            List<Appointment.AppointmentStatus> statuses = parseStatuses(status);
            if (!statuses.isEmpty()) {
                return appointmentRepository
                        .findByPatientIdAndStatusInOrderByCreatedAtDesc(patient.getId(), statuses, pageable)
                        .map(this::buildAppointmentResponse);
            }
        }

        return appointmentRepository
                .findByPatientIdOrderByCreatedAtDesc(patient.getId(), pageable)
                .map(this::buildAppointmentResponse);
    }

    // ── Shared: Get single appointment by ID ──────────────────────────────────
    public AppointmentResponse getAppointmentById(UUID id, User user) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id.toString()));

        assertCanAccessAppointment(appointment, user);

        return buildAppointmentResponse(appointment);
    }

    // ── Patient/Doctor: Cancel appointment ────────────────────────────────────
    @Transactional
    @CacheEvict(value = "slots", allEntries = true)
    public AppointmentResponse cancelAppointment(UUID id, User user, CancelRequest request) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id.toString()));

        assertCanAccessAppointment(appointment, user);

        if (appointment.getStatus() == Appointment.AppointmentStatus.COMPLETED ||
                appointment.getStatus() == Appointment.AppointmentStatus.CANCELLED) {
            throw new BusinessException("CANNOT_CANCEL",
                    "This appointment cannot be cancelled in its current status.");
        }

        appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
        appointment.setCancelledBy(user.getId());
        appointment.setCancelReason(request != null ? request.getReason() : "Cancelled by user");
        appointmentRepository.save(appointment);

        // Release the time slot so other patients can book it
        slotRepository.findById(appointment.getSlotId()).ifPresent(slot -> {
            slot.setStatus(TimeSlot.SlotStatus.AVAILABLE);
            slotRepository.save(slot);
        });

        log.info("Appointment {} cancelled by user {}", id, user.getId());
        return buildAppointmentResponse(appointment);
    }

    // ── Doctor: Start the consultation (CONFIRMED → IN_PROGRESS) ─────────────
    // Called when the doctor joins the video room. Android/Web should call this
    // endpoint when the WebRTC call is established.
    @Transactional
    public AppointmentResponse startConsultation(UUID id, User doctorUser) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id.toString()));

        // Only the assigned doctor can start this appointment
        UUID doctorId = getDoctorIdForUser(doctorUser);
        if (!appointment.getDoctorId().equals(doctorId)) {
            throw new BusinessException("FORBIDDEN", "You are not the assigned doctor for this appointment.");
        }

        if (appointment.getStatus() != Appointment.AppointmentStatus.CONFIRMED) {
            throw new BusinessException("INVALID_STATE",
                    "Only CONFIRMED appointments can be started. Current: " + appointment.getStatus());
        }

        appointment.setStatus(Appointment.AppointmentStatus.IN_PROGRESS);
        appointmentRepository.save(appointment);

        log.info("Appointment {} started by doctor {}", id, doctorUser.getId());
        return buildAppointmentResponse(appointment);
    }

    // ── Doctor: Complete consultation + save clinical notes ───────────────────
    // Called when the doctor ends the call and submits their clinical output.
    // State: IN_PROGRESS → COMPLETED. Notes, diagnosis, prescription are saved permanently.
    @Transactional
    public AppointmentResponse completeAppointment(UUID id, User doctorUser, CompleteAppointmentRequest request) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id.toString()));

        UUID doctorId = getDoctorIdForUser(doctorUser);
        if (!appointment.getDoctorId().equals(doctorId)) {
            throw new BusinessException("FORBIDDEN", "You are not the assigned doctor for this appointment.");
        }

        // Guard: prevents double-completion
        if (appointment.getStatus() != Appointment.AppointmentStatus.IN_PROGRESS) {
            throw new BusinessException("INVALID_STATE",
                    "Only IN_PROGRESS appointments can be completed. Current: " + appointment.getStatus());
        }

        appointment.setStatus(Appointment.AppointmentStatus.COMPLETED);
        appointment.setNotes(request.getNotes());
        appointment.setDiagnosis(request.getDiagnosis());
        appointment.setPrescription(request.getPrescription());
        appointmentRepository.save(appointment);

        log.info("Appointment {} COMPLETED by doctor {} — notes saved", id, doctorUser.getId());
        return buildAppointmentResponse(appointment);
    }

    // ── Doctor: View their own appointment schedule ───────────────────────────
    // Doctors filter by their doctorId — different from patients who filter by patientId
    public Page<AppointmentResponse> getDoctorAppointments(User doctorUser, String status, int page, int size) {
        UUID doctorId = getDoctorIdForUser(doctorUser);
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        if (status != null && !status.isBlank()) {
            List<Appointment.AppointmentStatus> statuses = parseStatuses(status);
            if (!statuses.isEmpty()) {
                return appointmentRepository
                        .findByDoctorIdAndStatusInOrderByCreatedAtDesc(doctorId, statuses, pageable)
                        .map(this::buildAppointmentResponse);
            }
        }
        return appointmentRepository
                .findByDoctorIdOrderByCreatedAtDesc(doctorId, pageable)
                .map(this::buildAppointmentResponse);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private void assertCanAccessAppointment(Appointment appointment, User user) {
        if (user.getRole() == User.Role.ADMIN) {
            return;
        }
        if (user.getRole() == User.Role.DOCTOR) {
            UUID doctorId = getDoctorIdForUser(user);
            if (appointment.getDoctorId().equals(doctorId)) {
                return;
            }
        } else {
            var patientOpt = patientProfileRepository.findByUserId(user.getId());
            if (patientOpt.isPresent() && appointment.getPatientId().equals(patientOpt.get().getId())) {
                return;
            }
        }
        throw new BusinessException("FORBIDDEN", "You do not have access to this appointment.");
    }

    /**
     * Resolves a comma-separated status string ("PENDING,CONFIRMED") into a list of enums.
     * Invalid values are silently ignored (tolerant parsing for Android query params).
     */
    private List<Appointment.AppointmentStatus> parseStatuses(String statusParam) {
        return Arrays.stream(statusParam.split(","))
                .map(String::trim)
                .map(s -> {
                    try { return Appointment.AppointmentStatus.valueOf(s.toUpperCase()); }
                    catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * A doctor logs in as a User (ROLE_DOCTOR). Their Doctor profile carries the
     * doctorId referenced on appointments. This resolves that mapping.
     */
    private UUID getDoctorIdForUser(User user) {
        return doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("DOCTOR_NOT_FOUND",
                        "No doctor profile found for this user."))
                .getId();
    }

    private AppointmentResponse buildAppointmentResponse(Appointment appointment) {
        TimeSlot slot = slotRepository.findById(appointment.getSlotId()).orElse(null);
        return buildAppointmentResponse(appointment, slot);
    }

    private AppointmentResponse buildAppointmentResponse(Appointment appointment, TimeSlot slot) {
        Doctor doctor = doctorRepository.findById(appointment.getDoctorId()).orElse(null);
        String docName     = doctor != null ? doctor.getFullName()    : null;
        String docSpecialty= doctor != null ? doctor.getSpecialty()   : null;
        String docImage    = doctor != null ? doctor.getProfileImage(): null;
        return AppointmentResponse.from(appointment, slot, docName, docSpecialty, docImage);
    }
}