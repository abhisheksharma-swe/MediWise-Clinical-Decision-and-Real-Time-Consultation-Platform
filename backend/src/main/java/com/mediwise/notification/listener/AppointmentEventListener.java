package com.mediwise.notification.listener;

import com.mediwise.appointment.event.AppointmentBookedEvent;
import com.mediwise.appointment.event.AppointmentCancelledEvent;
import com.mediwise.appointment.event.AppointmentCompletedEvent;
import com.mediwise.appointment.event.AppointmentConfirmedEvent;
import com.mediwise.appointment.event.AppointmentNoShowEvent;
import com.mediwise.appointment.event.AppointmentStartedEvent;
import com.mediwise.appointment.model.Appointment;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.notification.service.NotificationService;
import com.mediwise.profile.repository.PatientProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Turns committed appointment-lifecycle events into persisted + realtime +
 * push notifications for the affected patient and doctor.
 *
 * Every handler is a {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * rather than a plain {@code @EventListener}: the triggering service methods
 * publish their event from inside a {@code @Transactional} method, so a plain
 * listener could fire before (or racing) the actual database commit — a
 * notification for a booking/cancellation that then rolls back. AFTER_COMMIT
 * guarantees the transition is durable before anyone is notified about it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentEventListener {

    private final NotificationService notificationService;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorRepository doctorRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentBooked(AppointmentBookedEvent event) {
        Appointment appt = event.getAppointment();
        if (appt == null) return;

        notifyPatient(appt, "Appointment Scheduled",
                "Your appointment has been scheduled successfully.", "APPOINTMENT_BOOKED");
        notifyDoctor(appt, "New Appointment Request",
                "You have a new appointment request from a patient.", "NEW_APPOINTMENT");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentConfirmed(AppointmentConfirmedEvent event) {
        Appointment appt = event.getAppointment();
        if (appt == null) return;

        notifyPatient(appt, "Appointment Confirmed",
                "Your payment was received and your appointment is confirmed.", "APPOINTMENT_CONFIRMED");
        notifyDoctor(appt, "Appointment Confirmed",
                "A patient's appointment has been confirmed after payment.", "APPOINTMENT_CONFIRMED");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCancelled(AppointmentCancelledEvent event) {
        Appointment appt = event.getAppointment();
        if (appt == null) return;

        notifyPatient(appt, "Appointment Cancelled",
                "Your appointment has been cancelled.", "APPOINTMENT_CANCELLED");
        notifyDoctor(appt, "Appointment Cancelled",
                "An appointment on your schedule has been cancelled.", "APPOINTMENT_CANCELLED");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentStarted(AppointmentStartedEvent event) {
        Appointment appt = event.getAppointment();
        if (appt == null) return;

        notifyPatient(appt, "Consultation Started",
                "Your doctor has started the consultation. Join now.", "APPOINTMENT_STARTED");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCompleted(AppointmentCompletedEvent event) {
        Appointment appt = event.getAppointment();
        if (appt == null) return;

        notifyPatient(appt, "Consultation Completed",
                "Your consultation notes are ready to view.", "APPOINTMENT_COMPLETED");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentNoShow(AppointmentNoShowEvent event) {
        Appointment appt = event.getAppointment();
        if (appt == null) return;

        notifyPatient(appt, "Missed Appointment",
                "You missed your scheduled appointment.", "APPOINTMENT_NO_SHOW");
    }

    private void notifyPatient(Appointment appt, String title, String body, String type) {
        if (appt.getPatientId() == null) return;
        patientProfileRepository.findById(appt.getPatientId())
                .ifPresent(patient -> safeSend(patient.getUserId(), title, body, type, appt.getId()));
    }

    private void notifyDoctor(Appointment appt, String title, String body, String type) {
        if (appt.getDoctorId() == null) return;
        doctorRepository.findById(appt.getDoctorId())
                .ifPresent(doctor -> safeSend(doctor.getUserId(), title, body, type, appt.getId()));
    }

    private void safeSend(UUID userId, String title, String body, String type, UUID refId) {
        if (userId == null) return;
        try {
            notificationService.send(userId, title, body, type, refId);
        } catch (Exception e) {
            log.warn("Failed to deliver notification to user {}: {}", userId, e.getMessage());
        }
    }
}
