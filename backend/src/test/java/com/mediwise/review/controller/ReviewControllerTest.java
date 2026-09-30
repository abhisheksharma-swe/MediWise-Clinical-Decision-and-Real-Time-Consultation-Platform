package com.mediwise.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediwise.auth.model.User;
import com.mediwise.review.dto.ReviewResponse;
import com.mediwise.review.dto.SubmitReviewRequest;
import com.mediwise.review.service.ReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private ReviewController reviewController;

    private User patientUser;
    private UUID appointmentId;
    private UUID doctorId;
    private ReviewResponse sampleResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(reviewController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        patientUser = new User();
        patientUser.setId(UUID.randomUUID());
        patientUser.setRole(User.Role.PATIENT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(patientUser, null, List.of()));

        appointmentId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        sampleResponse = ReviewResponse.builder()
                .id(UUID.randomUUID())
                .appointmentId(appointmentId)
                .doctorId(doctorId)
                .patientName("Jane Doe")
                .rating(5)
                .reviewText("Great consultation")
                .createdAt(Instant.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/appointments/{id}/review submits a review")
    void submit_returnsCreatedReview() throws Exception {
        SubmitReviewRequest request = new SubmitReviewRequest();
        request.setRating(5);
        request.setReviewText("Great consultation");

        when(reviewService.submit(eq(appointmentId), any(User.class), any(SubmitReviewRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/appointments/" + appointmentId + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.patientName").value("Jane Doe"));
    }

    @Test
    @DisplayName("GET /api/v1/doctors/{id}/reviews returns a page of reviews for that doctor")
    void getForDoctor_returnsPage() throws Exception {
        when(reviewService.getForDoctor(eq(doctorId), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/v1/doctors/" + doctorId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].patientName").value("Jane Doe"));
    }
}
