package com.mediwise.followup.service;

import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.common.security.AppointmentAuthorizationService;
import com.mediwise.consultation.model.Consultation;
import com.mediwise.consultation.repository.ConsultationRepository;
import com.mediwise.followup.dto.CreateFollowUpRequest;
import com.mediwise.followup.dto.FollowUpResponse;
import com.mediwise.followup.model.FollowUp;
import com.mediwise.followup.repository.FollowUpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowUpService {

    private final FollowUpRepository followUpRepository;
    private final ConsultationRepository consultationRepository;
    private final AppointmentAuthorizationService authorizationService;

    @Transactional
    public FollowUpResponse create(UUID consultationId, User doctorUser, CreateFollowUpRequest request) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));

        UUID doctorId = authorizationService.requireDoctorId(doctorUser);
        if (!consultation.getDoctorId().equals(doctorId)) {
            throw new BusinessException("FORBIDDEN", "You are not the doctor for this consultation.");
        }

        FollowUp followUp = FollowUp.builder()
                .consultationId(consultationId)
                .patientId(consultation.getPatientId())
                .doctorId(consultation.getDoctorId())
                .recommendedDate(request.getRecommendedDate())
                .reason(request.getReason())
                .status(FollowUp.Status.PENDING)
                .build();

        return FollowUpResponse.from(followUpRepository.save(followUp));
    }

    public List<FollowUpResponse> getForPatient(UUID patientId, User user) {
        authorizationService.assertCanViewPatientHistory(patientId, user);
        return followUpRepository.findByPatientIdOrderByRecommendedDateAsc(patientId).stream()
                .map(FollowUpResponse::from).toList();
    }

    /** Self-service: the calling patient's own follow-ups (used for the "Upcoming Follow-ups" home widget). */
    public List<FollowUpResponse> getMyFollowUps(User user) {
        UUID patientId = authorizationService.resolvePatientId(user);
        if (patientId == null) {
            throw new ResourceNotFoundException("Patient profile", user.getId().toString());
        }
        return getForPatient(patientId, user);
    }

    @Transactional
    public FollowUpResponse dismiss(UUID id, User user) {
        FollowUp followUp = followUpRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FollowUp", id.toString()));
        UUID ownPatientId = authorizationService.resolvePatientId(user);
        if (ownPatientId == null || !followUp.getPatientId().equals(ownPatientId)) {
            throw new BusinessException("FORBIDDEN", "You can only dismiss your own follow-ups.");
        }
        followUp.setStatus(FollowUp.Status.DISMISSED);
        return FollowUpResponse.from(followUpRepository.save(followUp));
    }
}
