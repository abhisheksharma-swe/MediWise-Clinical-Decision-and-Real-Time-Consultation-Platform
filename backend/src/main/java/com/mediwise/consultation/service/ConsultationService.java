package com.mediwise.consultation.service;

import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.auth.model.User;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.common.security.AppointmentAuthorizationService;
import com.mediwise.consultation.dto.ConsultationResponse;
import com.mediwise.consultation.dto.UpdateConsultationRequest;
import com.mediwise.consultation.model.Consultation;
import com.mediwise.consultation.repository.ConsultationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsultationService {

    private final ConsultationRepository consultationRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentAuthorizationService authorizationService;

    /** Returns null (not an error) if the doctor hasn't started recording clinical content yet. */
    public ConsultationResponse getForAppointment(UUID appointmentId, User user) {
        Appointment appointment = loadAppointment(appointmentId);
        authorizationService.assertCanAccessAppointment(appointment, user);
        return consultationRepository.findByAppointmentId(appointmentId)
                .map(ConsultationResponse::from)
                .orElse(null);
    }

    @Transactional
    public ConsultationResponse upsert(UUID appointmentId, User doctorUser, UpdateConsultationRequest request) {
        Appointment appointment = loadAppointment(appointmentId);
        authorizationService.assertDoctorOwnsAppointment(appointment, doctorUser);

        Consultation consultation = consultationRepository.findByAppointmentId(appointmentId)
                .orElseGet(() -> Consultation.builder()
                        .appointmentId(appointmentId)
                        .patientId(appointment.getPatientId())
                        .doctorId(appointment.getDoctorId())
                        .build());

        if (StringUtils.hasText(request.getChiefComplaint())) consultation.setChiefComplaint(request.getChiefComplaint());
        if (request.getSymptoms() != null) consultation.setSymptoms(request.getSymptoms());
        if (request.getObservations() != null) consultation.setObservations(request.getObservations());
        if (request.getAssessment() != null) consultation.setAssessment(request.getAssessment());
        if (request.getTreatmentPlan() != null) consultation.setTreatmentPlan(request.getTreatmentPlan());
        if (request.getNotes() != null) consultation.setDoctorNotes(request.getNotes());
        consultation.setStatus(appointment.getStatus().name());

        return ConsultationResponse.from(consultationRepository.save(consultation));
    }

    public Page<ConsultationResponse> getPatientHistory(UUID patientId, User user, int page, int size) {
        authorizationService.assertCanViewPatientHistory(patientId, user);
        var pageable = PageRequest.of(page, Math.min(size, 50), Sort.by("createdAt").descending());
        return consultationRepository.findByPatientIdOrderByCreatedAtDesc(patientId, pageable)
                .map(ConsultationResponse::from);
    }

    /** Self-service: the calling patient's own history, mirroring AppointmentController's /history. */
    public Page<ConsultationResponse> getMyHistory(User user, int page, int size) {
        UUID patientId = authorizationService.resolvePatientId(user);
        if (patientId == null) {
            throw new ResourceNotFoundException("Patient profile", user.getId().toString());
        }
        return getPatientHistory(patientId, user, page, size);
    }

    private Appointment loadAppointment(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId.toString()));
    }
}
