package com.mediwise.appointment.service;

import com.mediwise.appointment.dto.AppointmentResponse;
import com.mediwise.appointment.dto.BookAppointmentRequest;
import com.mediwise.appointment.dto.CancelRequest;
import com.mediwise.appointment.dto.CompleteAppointmentRequest;
import com.mediwise.appointment.event.AppointmentBookedEvent;
import com.mediwise.appointment.event.AppointmentCancelledEvent;
import com.mediwise.appointment.event.AppointmentCompletedEvent;
import com.mediwise.appointment.event.AppointmentNoShowEvent;
import com.mediwise.appointment.event.AppointmentStartedEvent;
import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.common.exception.SlotConflictException;
import com.mediwise.common.exception.UnauthorizedException;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

        // Atomic conditional consume: only succeeds if this user still holds
        // the LOCKED slot at this instant. A read-then-save here would let two
        // near-simultaneous booking attempts for the same slot both pass a
        // stale in-memory check and create duplicate appointments.
        int consumed = slotRepository.tryConsumeLockedSlot(request.getSlotId(), user.getId());
        if (consumed == 0) {
            // Either the lock was never held/has expired, or this exact
            // request already succeeded once (client retry after a timeout) —
            // return the existing appointment idempotently instead of erroring.
            Appointment existing = appointmentRepository
                    .findFirstBySlotIdAndStatusNot(request.getSlotId(), Appointment.AppointmentStatus.CANCELLED)
                    .orElse(null);
            if (existing != null && existing.getPatientId().equals(patient.getId())) {
                log.info("Duplicate booking request for slot {} — returning existing appointment {}",
                        request.getSlotId(), existing.getId());
                return buildAppointmentResponse(existing, slot);
            }
            throw new SlotConflictException("Slot is not reserved for you. Please re-select.");
        }

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

    /** Completed consultation records visible to the patient or an authorized doctor. */
    public Page<AppointmentResponse> getConsultationHistory(UUID patientId, User user, int page, int size) {
        assertCanViewPatientHistory(patientId, user);
        Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by("createdAt").descending());
        return appointmentRepository
                .findByPatientIdAndStatusOrderByCreatedAtDesc(patientId, Appointment.AppointmentStatus.COMPLETED, pageable)
                .map(this::buildAppointmentResponse);
    }

    public UUID getPatientIdForHistory(User user) {
        return patientProfileRepository.findByUserId(user.getId())
                .map(PatientProfile::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile", user.getId().toString()));
    }

    private void assertCanViewPatientHistory(UUID patientId, User user) {
        if (user.getRole() == User.Role.ADMIN) {
            return;
        }
        if (user.getRole() == User.Role.PATIENT) {
            UUID ownPatientId = patientProfileRepository.findByUserId(user.getId())
                    .map(PatientProfile::getId).orElse(null);
            if (patientId.equals(ownPatientId)) return;
        } else if (user.getRole() == User.Role.DOCTOR) {
            UUID doctorId = doctorRepository.findByUserId(user.getId())
                    .map(com.mediwise.doctor.model.Doctor::getId).orElse(null);
            if (doctorId != null && appointmentRepository.existsByDoctorIdAndPatientId(doctorId, patientId)) return;
        }
        throw new UnauthorizedException("You do not have permission to view this patient's consultation history.");
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
        eventPublisher.publishEvent(new AppointmentCancelledEvent(this, appointment, user.getId()));
        return buildAppointmentResponse(appointment);
    }

    // ── System: Expire abandoned unpaid bookings ──────────────────────────────
    // A slot is consumed (BOOKED) the moment an appointment is created, before
    // payment completes — without this, a patient who never finishes payment
    // (closes the app, payment gateway error, etc.) would keep the slot BOOKED
    // forever, unusable by anyone else. Anything still PENDING after the
    // payment window is treated as abandoned: cancel it and free the slot.
    private static final long PAYMENT_WINDOW_MINUTES = 15;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    @CacheEvict(value = "slots", allEntries = true)
    public void expireAbandonedPendingAppointments() {
        Instant cutoff = Instant.now().minusSeconds(PAYMENT_WINDOW_MINUTES * 60);
        List<Appointment> stale = appointmentRepository
                .findByStatusAndCreatedAtBefore(Appointment.AppointmentStatus.PENDING, cutoff);

        for (Appointment appointment : stale) {
            appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
            appointment.setCancelReason("Payment was not completed within the allowed time window.");
            appointmentRepository.save(appointment);

            slotRepository.findById(appointment.getSlotId()).ifPresent(slot -> {
                slot.setStatus(TimeSlot.SlotStatus.AVAILABLE);
                slot.setLockedBy(null);
                slot.setLockedUntil(null);
                slotRepository.save(slot);
            });

            log.info("Appointment {} auto-cancelled — payment window ({} min) elapsed without confirmation",
                    appointment.getId(), PAYMENT_WINDOW_MINUTES);
            eventPublisher.publishEvent(new AppointmentCancelledEvent(this, appointment, null));
        }
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

        // Idempotent retry: the doctor's client already got this transition
        // through (e.g. a timed-out response), so return current state rather
        // than erroring on a harmless repeat of the same request.
        if (appointment.getStatus() == Appointment.AppointmentStatus.IN_PROGRESS) {
            return buildAppointmentResponse(appointment);
        }

        if (appointment.getStatus() != Appointment.AppointmentStatus.CONFIRMED) {
            throw new BusinessException("INVALID_STATE",
                    "Only CONFIRMED appointments can be started. Current: " + appointment.getStatus());
        }

        appointment.setStatus(Appointment.AppointmentStatus.IN_PROGRESS);
        appointmentRepository.save(appointment);

        log.info("Appointment {} started by doctor {}", id, doctorUser.getId());
        eventPublisher.publishEvent(new AppointmentStartedEvent(this, appointment));
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

        // Idempotent retry: return the already-saved clinical record rather
        // than erroring — and never let a resubmit silently overwrite notes
        // that were already committed.
        if (appointment.getStatus() == Appointment.AppointmentStatus.COMPLETED) {
            return buildAppointmentResponse(appointment);
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
        eventPublisher.publishEvent(new AppointmentCompletedEvent(this, appointment));
        return buildAppointmentResponse(appointment);
    }

    // ── Doctor: Mark the patient as a no-show ─────────────────────────────────
    // Only valid from CONFIRMED — the doctor is present but the patient never
    // joined/arrived. Distinct from CANCELLED (which any participant can trigger
    // ahead of time) and does not release the slot for rebooking.
    @Transactional
    public AppointmentResponse markNoShow(UUID id, User doctorUser) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id.toString()));

        UUID doctorId = getDoctorIdForUser(doctorUser);
        if (!appointment.getDoctorId().equals(doctorId)) {
            throw new BusinessException("FORBIDDEN", "You are not the assigned doctor for this appointment.");
        }

        if (appointment.getStatus() == Appointment.AppointmentStatus.NO_SHOW) {
            return buildAppointmentResponse(appointment);
        }

        if (appointment.getStatus() != Appointment.AppointmentStatus.CONFIRMED) {
            throw new BusinessException("INVALID_STATE",
                    "Only CONFIRMED appointments can be marked as no-show. Current: " + appointment.getStatus());
        }

        appointment.setStatus(Appointment.AppointmentStatus.NO_SHOW);
        appointmentRepository.save(appointment);

        log.info("Appointment {} marked NO_SHOW by doctor {}", id, doctorUser.getId());
        eventPublisher.publishEvent(new AppointmentNoShowEvent(this, appointment));
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
        UUID doctorUserId  = doctor != null ? doctor.getUserId()      : null;
        UUID patientUserId = patientProfileRepository.findById(appointment.getPatientId())
                .map(p -> p.getUserId()).orElse(null);
        String patientName = patientProfileRepository.findById(appointment.getPatientId())
            .map(PatientProfile::getFullName).orElse(null);
        return AppointmentResponse.from(appointment, slot, docName, docSpecialty, docImage, doctorUserId, patientUserId, patientName);
    }
}