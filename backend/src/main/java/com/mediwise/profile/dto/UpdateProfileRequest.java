package com.mediwise.profile.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateProfileRequest {
    private String fullName;
    private LocalDate dob;
    private String bloodType;
    private String gender;
    private String address;
    private String emergencyContact;
}
