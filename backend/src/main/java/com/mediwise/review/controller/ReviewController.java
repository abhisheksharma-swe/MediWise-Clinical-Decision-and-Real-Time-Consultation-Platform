package com.mediwise.review.controller;

import com.mediwise.auth.model.User;
import com.mediwise.common.response.ApiResponse;
import com.mediwise.common.response.PagedResponse;
import com.mediwise.review.dto.ReviewResponse;
import com.mediwise.review.dto.SubmitReviewRequest;
import com.mediwise.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Patient reviews of completed appointments")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/api/v1/appointments/{id}/review")
    @PreAuthorize("hasRole('PATIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Patient submits a review for a completed appointment")
    public ResponseEntity<ApiResponse<ReviewResponse>> submit(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody SubmitReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(reviewService.submit(id, user, request)));
    }

    @GetMapping("/api/v1/doctors/{id}/reviews")
    @Operation(summary = "List a doctor's reviews")
    public ResponseEntity<ApiResponse<PagedResponse<ReviewResponse>>> getForDoctor(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(reviewService.getForDoctor(id, page, size))));
    }
}
