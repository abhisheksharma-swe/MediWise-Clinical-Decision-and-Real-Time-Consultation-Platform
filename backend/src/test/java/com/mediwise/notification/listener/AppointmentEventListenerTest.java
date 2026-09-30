package com.mediwise.notification.listener;

import com.mediwise.appointment.event.AppointmentBookedEvent;
import com.mediwise.appointment.event.AppointmentCancelledEvent;
import com.mediwise.appointment.event.AppointmentRescheduledEvent;
import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.model.AppointmentStatusHistory;
import com.mediwise.appointment.repository.AppointmentStatusHistoryRepository;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.notification.service.NotificationService;
import com.mediwise.profile.repository.PatientProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the appointment_status_history audit-trail writes this listener owns.
 * Regression coverage for a real bug found and fixed this cycle: the reschedule
 * handler originally wrote old_status/new_status backwards (old_status=
 * "RESCHEDULED", new_status="CONFIRMED" — nonsensical, since the appointment's
 * actual status field never leaves CONFIRMED on a reschedule).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentEventListener — status history audit trail")
class AppointmentEventListenerTest {

    @Mock private NotificationService notificationService;
    @Mock private PatientProfileRepository patientProfileRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private AppointmentStatusHistoryRepository appointmentStatusHistoryRepository;

    @InjectMocks
    private AppointmentEventListener listener;

    private Appointment appointmentWithStatus(Appointment.AppointmentStatus status) {
        Appointment appt = new Appointment();
        appt.setId(UUID.randomUUID());
        appt.setPatientId(UUID.randomUUID());
        appt.setDoctorId(UUID.randomUUID());
        appt.setStatus(status);
        return appt;
    }

    @Test
    @DisplayName("booking records old_status=null, new_status=the appointment's current status")
    void onAppointmentBooked_recordsNewStatusOnly() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.PENDING);

        listener.onAppointmentBooked(new AppointmentBookedEvent(this, appt));

        ArgumentCaptor<AppointmentStatusHistory> captor = ArgumentCaptor.forClass(AppointmentStatusHistory.class);
        verify(appointmentStatusHistoryRepository).save(captor.capture());
        AppointmentStatusHistory history = captor.getValue();
        assertThat(history.getAppointmentId()).isEqualTo(appt.getId());
        assertThat(history.getOldStatus()).isNull();
        assertThat(history.getNewStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("cancellation records who cancelled it and the reason")
    void onAppointmentCancelled_recordsActorAndReason() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.CANCELLED);
        appt.setCancelReason("Patient requested reschedule");
        UUID cancelledBy = UUID.randomUUID();

        listener.onAppointmentCancelled(new AppointmentCancelledEvent(this, appt, cancelledBy));

        ArgumentCaptor<AppointmentStatusHistory> captor = ArgumentCaptor.forClass(AppointmentStatusHistory.class);
        verify(appointmentStatusHistoryRepository).save(captor.capture());
        AppointmentStatusHistory history = captor.getValue();
        assertThat(history.getChangedBy()).isEqualTo(cancelledBy);
        assertThat(history.getReason()).isEqualTo("Patient requested reschedule");
        assertThat(history.getNewStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("reschedule records new_status=RESCHEDULED (the audit marker) and old_status=the appointment's actual status (CONFIRMED) — not backwards")
    void onAppointmentRescheduled_recordsCorrectDirection_notBackwards() {
        // The appointment's real status field stays CONFIRMED across a reschedule —
        // only the slot changes — so this reflects what AppointmentService actually does.
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.CONFIRMED);
        UUID oldSlotId = UUID.randomUUID();

        listener.onAppointmentRescheduled(new AppointmentRescheduledEvent(this, appt, oldSlotId));

        ArgumentCaptor<AppointmentStatusHistory> captor = ArgumentCaptor.forClass(AppointmentStatusHistory.class);
        verify(appointmentStatusHistoryRepository).save(captor.capture());
        AppointmentStatusHistory history = captor.getValue();

        // This is the exact regression: new_status must be the "RESCHEDULED" marker,
        // and old_status must be the appointment's real prior status (CONFIRMED) —
        // reversing these was the bug found and fixed this cycle.
        assertThat(history.getNewStatus()).isEqualTo("RESCHEDULED");
        assertThat(history.getOldStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("a failure persisting status history never breaks notification delivery")
    void recordStatusChange_persistenceFailure_isSwallowed() {
        Appointment appt = appointmentWithStatus(Appointment.AppointmentStatus.PENDING);
        when(appointmentStatusHistoryRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        listener.onAppointmentBooked(new AppointmentBookedEvent(this, appt));

        // Notifications still attempted despite the history-write failure above.
        verify(patientProfileRepository).findById(appt.getPatientId());
    }

    @Test
    @DisplayName("a null appointment on the event is ignored, not a NullPointerException")
    void onAppointmentBooked_nullAppointment_doesNothing() {
        listener.onAppointmentBooked(new AppointmentBookedEvent(this, null));

        verifyNoInteractions(appointmentStatusHistoryRepository, notificationService);
    }
}
