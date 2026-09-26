package com.mediwise.ai.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Document(collection = "ai_reports")
@Getter
@Setter
public class AiReport {

    @Id
    private String id;

    @Indexed
    private UUID patientId;

    private UUID appointmentId;
    private String modelName;
    private String modelVersion;
    private int urgencyScore;
    private String suggestedSpecialty;
    private double confidence;
    private String recommendation;
    private List<String> riskFactors;

    /** Distinct from suggestedSpecialty — a first-class field for age-aware routing
     *  (e.g. "Pediatric Care", "Urgent Care", "Routine Consultation"). */
    private String careCategory;

    @Indexed
    private Instant createdAt;
}
