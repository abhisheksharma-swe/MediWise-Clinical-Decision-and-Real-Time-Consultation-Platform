package com.mediwise.auth.controller;

import com.mediwise.auth.dto.*;
import com.mediwise.auth.model.User;
import com.mediwise.auth.service.AuthService;
import com.mediwise.chat.util.ChatRateLimiter;
import com.mediwise.common.handler.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private AuthService authService;

    @Mock
    private ChatRateLimiter rateLimiter;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        lenient().when(rateLimiter.allow(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 429 once the rate limit is exceeded")
    void testLoginEndpoint_rateLimited() throws Exception {
        when(rateLimiter.allow(anyString(), anyInt(), any(Duration.class))).thenReturn(false);

        LoginRequest request = LoginRequest.builder()
                .emailOrPhone("jane@mediwise.com")
                .password("securePassword123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/config should return 200 with MediWise app config")
    void testGetAppConfigEndpoint() throws Exception {
        AppConfigResponse config = AppConfigResponse.builder()
                .appName("MediWise")
                .version("1.0.0")
                .status("HEALTHY")
                .environment("test")
                .features(List.of("auth_email_password", "ai_triage"))
                .build();

        when(authService.getAppConfig()).thenReturn(config);

        mockMvc.perform(get("/api/v1/auth/config")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.appName").value("MediWise"))
                .andExpect(jsonPath("$.data.status").value("HEALTHY"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register should return 201 with AuthResponse")
    void testRegisterEndpoint() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("Jane Doe")
                .email("jane@mediwise.com")
                .password("securePassword123")
                .phone("+1987654321")
                .dateOfBirth(LocalDate.of(1996, 7, 12))
                .role(User.Role.PATIENT)
                .build();

        AuthResponse response = AuthResponse.builder()
                .accessToken("mock.jwt.token")
                .refreshToken("mock.refresh.token")
                .expiresIn(900)
                .user(AuthResponse.UserInfo.builder()
                        .id(UUID.randomUUID())
                        .email("jane@mediwise.com")
                        .fullName("Jane Doe")
                        .role(User.Role.PATIENT)
                        .build())
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("mock.jwt.token"))
                .andExpect(jsonPath("$.data.user.email").value("jane@mediwise.com"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login should return 200 with AuthResponse")
    void testLoginEndpoint() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .emailOrPhone("jane@mediwise.com")
                .password("securePassword123")
                .build();

        AuthResponse response = AuthResponse.builder()
                .accessToken("mock.jwt.token")
                .refreshToken("mock.refresh.token")
                .expiresIn(900)
                .user(AuthResponse.UserInfo.builder()
                        .id(UUID.randomUUID())
                        .email("jane@mediwise.com")
                        .fullName("Jane Doe")
                        .role(User.Role.PATIENT)
                        .build())
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("mock.jwt.token"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password should return 200")
    void testResetPasswordEndpoint() throws Exception {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .emailOrPhone("jane@mediwise.com")
                .newPassword("newPassword456")
                .build();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
