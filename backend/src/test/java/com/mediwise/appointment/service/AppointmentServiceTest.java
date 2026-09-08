package com.mediwise.appointment.service;

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
import com.mediwise.common.exception.SlotConflictException;
import com.mediwise.doctor.model.Doctor;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.repository.PatientProfileRepository;
import com.mediwise.schedule.model.TimeSlot;
import com.mediwise.schedule.repository.TimeSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the appointment lifecycle transitions and their authorization rules,
 * and confirms each successful transition publishes the corresponding domain
 * event (the event is what the notification/realtime layer relies on).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentService — Lifecycle Transitions")
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private TimeSlotRepository slotRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AppointmentService appointmentService;

    private UUID appointmentId;
    private UUID patientId;
    private UUID doctorId;
    private UUID doctorUserId;
    private UUID patientUserId;
    private User doctorUser;
    private User patientUser;

    @BeforeEach
    void setUp() {
        appointmentId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        doctorUserId = UUID.randomUUID();
        patientUserId = UUID.randomUUID();

        doctorUser = User.builder().id(doctorUserId).role(User.Role.DOCTOR).build();
        patientUser = User.builder().id(patientUserId).role(User.Role.PATIENT).build();
    }

    private Appointment appointmentWithStatus(Appointment.AppointmentStatus status) {
        return Appointment.builder()
                .id(appointmentId)
                .patientId(patientId)
                .doctorId(doctorId)
                .slotId(UUID.randomUUID())
                .status(status)
                .build();
    }

    private void stubDoctorOwnsAppointment() {
        when(doctorRepository.findByUserId(doctorUserId))
                .thenReturn(Optional.of(Doctor.builder().id(doctorId).userId(doctorUserId).build()));
    }

    // ── cancel ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancel: patient can cancel their own PENDING appointment and an event is published")
    void cancel_success_publishesEvent() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.PENDING);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        when(patientProfileRepository.findByUserId(patientUserId))
                .thenReturn(Optional.of(PatientProfile.builder().id(patientId).userId(patientUserId).build()));

        appointmentService.cancelAppointment(appointmentId, patientUser, new CancelRequest());

        assertThat(appt.getStatus()).isEqualTo(Appointment.AppointmentStatus.CANCELLED);
        verify(appointmentRepository).save(appt);

        ArgumentCaptor<AppointmentCancelledEvent> captor = ArgumentCaptor.forClass(AppointmentCancelledEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("APPOINTMENT_CANCELLED");
        assertThat(captor.getValue().getAppointment()).isEqualTo(appt);
    }

    @Test
    @DisplayName("cancel: rejected once the appointment is already COMPLETED")
    void cancel_rejectsFromCompleted() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.COMPLETED);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        when(patientProfileRepository.findByUserId(patientUserId))
                .thenReturn(Optional.of(PatientProfile.builder().id(patientId).userId(patientUserId).build()));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(appointmentId, patientUser, new CancelRequest()))
                .isInstanceOf(BusinessException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("cancel: a stranger patient cannot cancel someone else's appointment")
    void cancel_forbidsNonOwner() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.PENDING);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        when(patientProfileRepository.findByUserId(patientUserId))
                .thenReturn(Optional.of(PatientProfile.builder().id(UUID.randomUUID()).userId(patientUserId).build()));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(appointmentId, patientUser, new CancelRequest()))
                .isInstanceOf(BusinessException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── start ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("start: assigned doctor can start a CONFIRMED appointment and an event is published")
    void start_success_publishesEvent() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        stubDoctorOwnsAppointment();

        appointmentService.startConsultation(appointmentId, doctorUser);

        assertThat(appt.getStatus()).isEqualTo(Appointment.AppointmentStatus.IN_PROGRESS);
        ArgumentCaptor<AppointmentStartedEvent> captor = ArgumentCaptor.forClass(AppointmentStartedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("APPOINTMENT_STARTED");
    }

    @Test
    @DisplayName("start: rejected when appointment is not CONFIRMED")
    void start_rejectsWrongState() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.PENDING);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        stubDoctorOwnsAppointment();

        assertThatThrownBy(() -> appointmentService.startConsultation(appointmentId, doctorUser))
                .isInstanceOf(BusinessException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("start: forbidden for a doctor who is not assigned to this appointment")
    void start_forbidsWrongDoctor() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        when(doctorRepository.findByUserId(doctorUserId))
                .thenReturn(Optional.of(Doctor.builder().id(UUID.randomUUID()).userId(doctorUserId).build()));

        assertThatThrownBy(() -> appointmentService.startConsultation(appointmentId, doctorUser))
                .isInstanceOf(BusinessException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("start: retrying after it already succeeded returns current state instead of erroring")
    void start_idempotentRetry_returnsCurrentState() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.IN_PROGRESS);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        stubDoctorOwnsAppointment();

        var response = appointmentService.startConsultation(appointmentId, doctorUser);

        assertThat(response.getStatus()).isEqualTo(Appointment.AppointmentStatus.IN_PROGRESS);
        verify(appointmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── complete ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("complete: assigned doctor can complete an IN_PROGRESS appointment and an event is published")
    void complete_success_publishesEvent() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.IN_PROGRESS);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        stubDoctorOwnsAppointment();

        CompleteAppointmentRequest request = new CompleteAppointmentRequest();
        request.setNotes("Patient responded well to treatment.");

        appointmentService.completeAppointment(appointmentId, doctorUser, request);

        assertThat(appt.getStatus()).isEqualTo(Appointment.AppointmentStatus.COMPLETED);
        assertThat(appt.getNotes()).isEqualTo("Patient responded well to treatment.");
        ArgumentCaptor<AppointmentCompletedEvent> captor = ArgumentCaptor.forClass(AppointmentCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("APPOINTMENT_COMPLETED");
    }

    @Test
    @DisplayName("complete: rejected when appointment is not IN_PROGRESS")
    void complete_rejectsWrongState() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        stubDoctorOwnsAppointment();

        CompleteAppointmentRequest request = new CompleteAppointmentRequest();
        request.setNotes("notes");

        assertThatThrownBy(() -> appointmentService.completeAppointment(appointmentId, doctorUser, request))
                .isInstanceOf(BusinessException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("complete: retrying after it already succeeded returns the saved record without overwriting notes")
    void complete_idempotentRetry_doesNotOverwriteNotes() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.COMPLETED);
        appt.setNotes("Original notes from the first successful call");
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        stubDoctorOwnsAppointment();

        CompleteAppointmentRequest retry = new CompleteAppointmentRequest();
        retry.setNotes("A different resubmit that should NOT overwrite the record");

        var response = appointmentService.completeAppointment(appointmentId, doctorUser, retry);

        assertThat(response.getStatus()).isEqualTo(Appointment.AppointmentStatus.COMPLETED);
        assertThat(appt.getNotes()).isEqualTo("Original notes from the first successful call");
        verify(appointmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── no-show ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no-show: assigned doctor can mark a CONFIRMED appointment as NO_SHOW and an event is published")
    void noShow_success_publishesEvent() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        stubDoctorOwnsAppointment();

        appointmentService.markNoShow(appointmentId, doctorUser);

        assertThat(appt.getStatus()).isEqualTo(Appointment.AppointmentStatus.NO_SHOW);
        ArgumentCaptor<AppointmentNoShowEvent> captor = ArgumentCaptor.forClass(AppointmentNoShowEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("APPOINTMENT_NO_SHOW");
    }

    @Test
    @DisplayName("no-show: rejected when appointment is not CONFIRMED")
    void noShow_rejectsWrongState() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.IN_PROGRESS);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        stubDoctorOwnsAppointment();

        assertThatThrownBy(() -> appointmentService.markNoShow(appointmentId, doctorUser))
                .isInstanceOf(BusinessException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("no-show: retrying after it already succeeded returns current state instead of erroring")
    void noShow_idempotentRetry_returnsCurrentState() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.NO_SHOW);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        stubDoctorOwnsAppointment();

        var response = appointmentService.markNoShow(appointmentId, doctorUser);

        assertThat(response.getStatus()).isEqualTo(Appointment.AppointmentStatus.NO_SHOW);
        verify(appointmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── book (slot-consumption race + idempotent retry) ────────────────────────

    private BookAppointmentRequest bookRequest(UUID slotId) {
        BookAppointmentRequest request = new BookAppointmentRequest();
        request.setSlotId(slotId);
        request.setDoctorId(doctorId);
        return request;
    }

    @Test
    @DisplayName("book: succeeds when the atomic slot-consume finds the lock still held, and publishes an event")
    void book_success_publishesEvent() {
        UUID slotId = UUID.randomUUID();
        BookAppointmentRequest request = bookRequest(slotId);

        when(patientProfileRepository.findByUserId(patientUserId))
                .thenReturn(Optional.of(PatientProfile.builder().id(patientId).userId(patientUserId).build()));
        when(doctorRepository.findById(doctorId))
                .thenReturn(Optional.of(Doctor.builder().id(doctorId).verified(true)
                        .consultationFee(BigDecimal.TEN).build()));
        when(slotRepository.findById(slotId))
                .thenReturn(Optional.of(TimeSlot.builder().id(slotId).doctorId(doctorId)
                        .status(TimeSlot.SlotStatus.LOCKED).lockedBy(patientUserId).build()));
        when(slotRepository.tryConsumeLockedSlot(slotId, patientUserId)).thenReturn(1);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        appointmentService.bookAppointment(patientUser, request);

        ArgumentCaptor<AppointmentBookedEvent> captor = ArgumentCaptor.forClass(AppointmentBookedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("APPOINTMENT_BOOKED");
    }

    @Test
    @DisplayName("book: a retried request for a slot this patient already booked returns the existing appointment, not a duplicate")
    void book_duplicateRetry_returnsExistingAppointment_noNewEvent() {
        UUID slotId = UUID.randomUUID();
        BookAppointmentRequest request = bookRequest(slotId);
        Appointment alreadyBooked = Appointment.builder()
                .id(UUID.randomUUID()).patientId(patientId).doctorId(doctorId).slotId(slotId)
                .status(Appointment.AppointmentStatus.PENDING).build();

        when(patientProfileRepository.findByUserId(patientUserId))
                .thenReturn(Optional.of(PatientProfile.builder().id(patientId).userId(patientUserId).build()));
        when(doctorRepository.findById(doctorId))
                .thenReturn(Optional.of(Doctor.builder().id(doctorId).verified(true)
                        .consultationFee(BigDecimal.TEN).build()));
        when(slotRepository.findById(slotId))
                .thenReturn(Optional.of(TimeSlot.builder().id(slotId).doctorId(doctorId)
                        .status(TimeSlot.SlotStatus.BOOKED).build()));
        // Lost the race against itself: the first attempt already consumed the slot.
        when(slotRepository.tryConsumeLockedSlot(slotId, patientUserId)).thenReturn(0);
        when(appointmentRepository.findFirstBySlotIdAndStatusNot(slotId, Appointment.AppointmentStatus.CANCELLED))
                .thenReturn(Optional.of(alreadyBooked));

        var response = appointmentService.bookAppointment(patientUser, request);

        assertThat(response.getId()).isEqualTo(alreadyBooked.getId());
        verify(appointmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("book: a genuine conflict (slot taken by someone else / lock expired) is rejected")
    void book_genuineConflict_throws() {
        UUID slotId = UUID.randomUUID();
        BookAppointmentRequest request = bookRequest(slotId);

        when(patientProfileRepository.findByUserId(patientUserId))
                .thenReturn(Optional.of(PatientProfile.builder().id(patientId).userId(patientUserId).build()));
        when(doctorRepository.findById(doctorId))
                .thenReturn(Optional.of(Doctor.builder().id(doctorId).verified(true)
                        .consultationFee(BigDecimal.TEN).build()));
        when(slotRepository.findById(slotId))
                .thenReturn(Optional.of(TimeSlot.builder().id(slotId).doctorId(doctorId)
                        .status(TimeSlot.SlotStatus.BOOKED).build()));
        when(slotRepository.tryConsumeLockedSlot(slotId, patientUserId)).thenReturn(0);
        when(appointmentRepository.findFirstBySlotIdAndStatusNot(slotId, Appointment.AppointmentStatus.CANCELLED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.bookAppointment(patientUser, request))
                .isInstanceOf(SlotConflictException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── expireAbandonedPendingAppointments ──────────────────────────────────────

    @Test
    @DisplayName("expiry job: cancels stale PENDING appointments, releases their slot, and publishes a cancellation event")
    void expireAbandoned_cancelsAndReleasesSlot() {
        UUID slotId = UUID.randomUUID();
        Appointment stale = appointmentWithStatus(Appointment.AppointmentStatus.PENDING);
        stale.setSlotId(slotId);
        when(appointmentRepository.findByStatusAndCreatedAtBefore(eq(Appointment.AppointmentStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(stale));
        when(slotRepository.findById(slotId))
                .thenReturn(Optional.of(TimeSlot.builder().id(slotId).status(TimeSlot.SlotStatus.BOOKED).build()));

        appointmentService.expireAbandonedPendingAppointments();

        assertThat(stale.getStatus()).isEqualTo(Appointment.AppointmentStatus.CANCELLED);
        verify(appointmentRepository).save(stale);

        ArgumentCaptor<TimeSlot> slotCaptor = ArgumentCaptor.forClass(TimeSlot.class);
        verify(slotRepository).save(slotCaptor.capture());
        assertThat(slotCaptor.getValue().getStatus()).isEqualTo(TimeSlot.SlotStatus.AVAILABLE);

        ArgumentCaptor<AppointmentCancelledEvent> captor = ArgumentCaptor.forClass(AppointmentCancelledEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getCancelledBy()).isNull();
    }
}
