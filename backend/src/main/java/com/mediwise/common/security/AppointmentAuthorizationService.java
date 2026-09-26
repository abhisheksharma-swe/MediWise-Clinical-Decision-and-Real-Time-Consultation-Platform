package com.mediwise.common.security;

import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.UnauthorizedException;
import com.mediwise.doctor.model.Doctor;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.repository.PatientProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Shared ownership/access checks for anything scoped to a patient or an appointment.
 * Extracted from AppointmentService/AiService, which independently reimplemented the same
 * "is this patient me, or a doctor who has treated this patient, or an admin" logic —
 * every new clinical domain (consultation, prescription, medical record, follow-up) needs
 * the identical check, so it lives here once.
 */
@Service
@RequiredArgsConstructor
public class AppointmentAuthorizationService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientProfileRepository patientProfileRepository;

    /**
     * Allows: the patient themselves, a doctor who has (or has had) at least one appointment
     * with that patient, or an admin. Used for anything patient-scoped but not tied to a single
     * appointment (consultation history, medical records, AI reports, follow-ups).
     */
    public void assertCanViewPatientHistory(UUID patientId, User user) {
        if (user.getRole() == User.Role.ADMIN) {
            return;
        }
        if (user.getRole() == User.Role.PATIENT) {
            if (patientId.equals(resolvePatientId(user))) return;
        } else if (user.getRole() == User.Role.DOCTOR) {
            UUID doctorId = resolveDoctorId(user);
            if (doctorId != null && appointmentRepository.existsByDoctorIdAndPatientId(doctorId, patientId)) return;
        }
        throw new UnauthorizedException("You do not have permission to view this patient's history.");
    }

    /** Allows: the patient or doctor on the appointment, or an admin. */
    public void assertCanAccessAppointment(Appointment appointment, User user) {
        if (user.getRole() == User.Role.ADMIN) {
            return;
        }
        if (user.getRole() == User.Role.DOCTOR) {
            UUID doctorId = resolveDoctorId(user);
            if (doctorId != null && appointment.getDoctorId().equals(doctorId)) return;
        } else {
            UUID patientId = resolvePatientId(user);
            if (patientId != null && appointment.getPatientId().equals(patientId)) return;
        }
        throw new BusinessException("FORBIDDEN", "You do not have access to this appointment.");
    }

    /** Resolves the calling doctor's `Doctor.id` and asserts they own the given appointment. */
    public UUID assertDoctorOwnsAppointment(Appointment appointment, User doctorUser) {
        UUID doctorId = requireDoctorId(doctorUser);
        if (!appointment.getDoctorId().equals(doctorId)) {
            throw new BusinessException("FORBIDDEN", "You are not the assigned doctor for this appointment.");
        }
        return doctorId;
    }

    public UUID resolveDoctorId(User user) {
        return doctorRepository.findByUserId(user.getId()).map(Doctor::getId).orElse(null);
    }

    public UUID requireDoctorId(User user) {
        return doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("DOCTOR_NOT_FOUND", "No doctor profile found for this user."))
                .getId();
    }

    public UUID resolvePatientId(User user) {
        return patientProfileRepository.findByUserId(user.getId()).map(PatientProfile::getId).orElse(null);
    }
}
