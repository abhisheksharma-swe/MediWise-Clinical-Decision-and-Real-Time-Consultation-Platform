package com.mediwise.prescription.service;

import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.common.security.AppointmentAuthorizationService;
import com.mediwise.consultation.model.Consultation;
import com.mediwise.consultation.repository.ConsultationRepository;
import com.mediwise.prescription.dto.CreatePrescriptionRequest;
import com.mediwise.prescription.dto.PrescriptionItemResponse;
import com.mediwise.prescription.dto.PrescriptionResponse;
import com.mediwise.prescription.model.Prescription;
import com.mediwise.prescription.model.PrescriptionItem;
import com.mediwise.prescription.repository.PrescriptionItemRepository;
import com.mediwise.prescription.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final ConsultationRepository consultationRepository;
    private final AppointmentAuthorizationService authorizationService;

    @Transactional
    public PrescriptionResponse create(UUID consultationId, User doctorUser, CreatePrescriptionRequest request) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));

        UUID doctorId = authorizationService.requireDoctorId(doctorUser);
        if (!consultation.getDoctorId().equals(doctorId)) {
            throw new BusinessException("FORBIDDEN", "You are not the doctor for this consultation.");
        }

        Prescription prescription = Prescription.builder()
                .consultationId(consultationId)
                .patientId(consultation.getPatientId())
                .doctorId(consultation.getDoctorId())
                .notes(request.getNotes())
                .build();
        prescription = prescriptionRepository.save(prescription);

        List<PrescriptionItem> items = new java.util.ArrayList<>();
        int order = 0;
        for (var itemReq : request.getItems()) {
            items.add(PrescriptionItem.builder()
                    .prescriptionId(prescription.getId())
                    .medicineName(itemReq.getMedicineName())
                    .dosage(itemReq.getDosage())
                    .frequency(itemReq.getFrequency())
                    .duration(itemReq.getDuration())
                    .instructions(itemReq.getInstructions())
                    .beforeAfterFood(itemReq.getBeforeAfterFood())
                    .sortOrder(order++)
                    .build());
        }
        items = prescriptionItemRepository.saveAll(items);

        return PrescriptionResponse.from(prescription, items.stream().map(PrescriptionItemResponse::from).toList());
    }

    public PrescriptionResponse getById(UUID id, User user) {
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", id.toString()));
        authorizationService.assertCanViewPatientHistory(prescription.getPatientId(), user);
        List<PrescriptionItemResponse> items = prescriptionItemRepository
                .findByPrescriptionIdOrderBySortOrderAsc(id).stream()
                .map(PrescriptionItemResponse::from).toList();
        return PrescriptionResponse.from(prescription, items);
    }

    public Page<PrescriptionResponse> getForPatient(UUID patientId, User user, int page, int size) {
        authorizationService.assertCanViewPatientHistory(patientId, user);
        Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by("createdAt").descending());
        Page<Prescription> prescriptions = prescriptionRepository.findByPatientIdOrderByCreatedAtDesc(patientId, pageable);

        List<UUID> prescriptionIds = prescriptions.getContent().stream().map(Prescription::getId).toList();
        Map<UUID, List<PrescriptionItem>> itemsByPrescriptionId = prescriptionItemRepository
                .findByPrescriptionIdInOrderBySortOrderAsc(prescriptionIds).stream()
                .collect(Collectors.groupingBy(PrescriptionItem::getPrescriptionId));

        return prescriptions.map(p -> PrescriptionResponse.from(p,
                itemsByPrescriptionId.getOrDefault(p.getId(), List.of()).stream()
                        .map(PrescriptionItemResponse::from).toList()));
    }

    /** Self-service: the calling patient's own prescriptions. */
    public Page<PrescriptionResponse> getMyPrescriptions(User user, int page, int size) {
        UUID patientId = authorizationService.resolvePatientId(user);
        if (patientId == null) {
            throw new ResourceNotFoundException("Patient profile", user.getId().toString());
        }
        return getForPatient(patientId, user, page, size);
    }
}
