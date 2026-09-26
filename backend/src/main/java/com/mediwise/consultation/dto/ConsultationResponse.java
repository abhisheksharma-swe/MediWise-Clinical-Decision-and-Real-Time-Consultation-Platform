package com.mediwise.consultation.dto;

import com.mediwise.consultation.model.Consultation;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class ConsultationResponse {
    private UUID id;
    private UUID appointmentId;
    private UUID patientId;
    private UUID doctorId;
    private String chiefComplaint;
    private List<String> symptoms;
    private String observations;
    private String assessment;
    private String treatmentPlan;
    private String doctorNotes;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public static ConsultationResponse from(Consultation c) {
        return ConsultationResponse.builder()
                .id(c.getId())
                .appointmentId(c.getAppointmentId())
                .patientId(c.getPatientId())
                .doctorId(c.getDoctorId())
                .chiefComplaint(c.getChiefComplaint())
                .symptoms(c.getSymptoms())
                .observations(c.getObservations())
                .assessment(c.getAssessment())
                .treatmentPlan(c.getTreatmentPlan())
                .doctorNotes(c.getDoctorNotes())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
