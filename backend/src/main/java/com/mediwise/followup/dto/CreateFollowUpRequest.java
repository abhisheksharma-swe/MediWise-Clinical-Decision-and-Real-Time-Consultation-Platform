package com.mediwise.followup.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateFollowUpRequest {
    private LocalDate recommendedDate;
    private String reason;
}
