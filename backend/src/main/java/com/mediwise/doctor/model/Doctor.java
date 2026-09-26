package com.mediwise.doctor.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "doctors")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", unique = true, nullable = false)
    private UUID userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(nullable = false, length = 100)
    private String specialty;

    @Column(name = "license_number", unique = true)
    private String licenseNumber;

    @Column(name = "experience_yrs")
    private Integer experienceYears;

    @Column(name = "consultation_fee", precision = 10, scale = 2)
    private BigDecimal consultationFee;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "total_reviews")
    @Builder.Default
    private Integer totalReviews = 0;

    @Column(name = "is_available")
    @Builder.Default
    private boolean available = true;

    @Builder.Default
    private boolean verified = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "doctor_specialties", joinColumns = @JoinColumn(name = "doctor_id"))
    @Column(name = "specialty")
    @Builder.Default
    private Set<String> specialties = new HashSet<>();

    @Column(name = "clinic_name")
    private String clinicName;

    @Column(name = "clinic_address", columnDefinition = "TEXT")
    private String clinicAddress;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "doctor_consultation_modes", joinColumns = @JoinColumn(name = "doctor_id"))
    @Column(name = "mode")
    @Builder.Default
    private Set<String> consultationModes = new HashSet<>(Set.of("ONLINE"));

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
