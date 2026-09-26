package com.mediwise.consultation.dto;

import lombok.Data;

import java.util.List;

/** Doctor-submitted clinical content for a consultation. All fields optional/partial-update. */
@Data
public class UpdateConsultationRequest {
    private String chiefComplaint;
    private List<String> symptoms;
    private String observations;
    private String assessment;
    private String treatmentPlan;
    private String notes;
}
