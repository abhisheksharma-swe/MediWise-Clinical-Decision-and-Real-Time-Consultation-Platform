package com.mediwise.prescription.dto;

import com.mediwise.prescription.model.PrescriptionItem;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data @Builder
public class PrescriptionItemResponse {
    private UUID id;
    private String medicineName;
    private String dosage;
    private String frequency;
    private String duration;
    private String instructions;
    private PrescriptionItem.BeforeAfterFood beforeAfterFood;
    private int sortOrder;

    public static PrescriptionItemResponse from(PrescriptionItem item) {
        return PrescriptionItemResponse.builder()
                .id(item.getId())
                .medicineName(item.getMedicineName())
                .dosage(item.getDosage())
                .frequency(item.getFrequency())
                .duration(item.getDuration())
                .instructions(item.getInstructions())
                .beforeAfterFood(item.getBeforeAfterFood())
                .sortOrder(item.getSortOrder())
                .build();
    }
}
