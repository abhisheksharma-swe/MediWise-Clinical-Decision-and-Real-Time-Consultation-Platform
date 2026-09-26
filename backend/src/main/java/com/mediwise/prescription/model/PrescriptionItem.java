package com.mediwise.prescription.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "prescription_items")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "prescription_id", nullable = false)
    private UUID prescriptionId;

    @Column(name = "medicine_name", nullable = false)
    private String medicineName;

    private String dosage;

    private String frequency;

    private String duration;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_after_food", length = 20)
    private BeforeAfterFood beforeAfterFood;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    public enum BeforeAfterFood {
        BEFORE_FOOD, AFTER_FOOD, NOT_APPLICABLE
    }
}
