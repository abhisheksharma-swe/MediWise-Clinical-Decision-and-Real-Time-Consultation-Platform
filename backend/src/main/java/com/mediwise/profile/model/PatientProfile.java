package com.mediwise.profile.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patient_profiles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PatientProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", unique = true, nullable = false)
    private UUID userId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "blood_type", length = 5)
    private String bloodType;

    @Column(length = 10)
    private String gender;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "emergency_contact", columnDefinition = "TEXT")
    private String emergencyContact;

    /**
     * Not persisted on this table — copied over from {@code User.phone} by
     * {@code ProfileService} so API consumers can read/display the phone
     * number alongside the rest of the profile without a schema change.
     */
    @Transient
    private String phone;
}
