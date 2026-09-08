package com.mediwise.auth.service;

import com.mediwise.auth.dto.*;
import com.mediwise.auth.model.User;
import com.mediwise.auth.repository.UserRepository;
import com.mediwise.auth.security.FirebaseTokenVerifier;
import com.google.firebase.auth.FirebaseToken;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.UnauthorizedException;
import com.mediwise.common.util.JwtUtil;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.repository.PatientProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PatientProfileRepository patientProfileRepository;

    @Mock
    private com.mediwise.doctor.repository.DoctorRepository doctorRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;
    private UUID sampleUserId;

    @BeforeEach
    void setUp() {
        // AuthService's @Autowired(required=false) redisTemplate field isn't part of
        // the Lombok-generated constructor, so Mockito's constructor-injection strategy
        // for @InjectMocks never reaches it — wire it in manually, same as RazorpayServiceTest
        // does for its @Value fields.
        ReflectionTestUtils.setField(authService, "redisTemplate", redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        sampleUserId = UUID.randomUUID();
        sampleUser = User.builder()
                .id(sampleUserId)
                .email("test@mediwise.com")
                .fullName("John Doe")
                .phone("+1234567890")
                .dob(LocalDate.of(1995, 5, 20))
                .passwordHash("hashedPassword123")
                .role(User.Role.PATIENT)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Should successfully register new user and create PatientProfile")
    void testRegisterSuccess() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("John Doe")
                .email("test@mediwise.com")
                .password("secret123")
                .phone("+1234567890")
                .dateOfBirth(LocalDate.of(1995, 5, 20))
                .firebaseIdToken("verified-firebase-token")
                .role(User.Role.PATIENT)
                .build();

        when(userRepository.existsByEmail("test@mediwise.com")).thenReturn(false);
        when(userRepository.existsByPhone("+1234567890")).thenReturn(false);
        FirebaseToken registrationToken = mock(FirebaseToken.class);
        when(registrationToken.getUid()).thenReturn("firebase_uid_123");
        when(firebaseTokenVerifier.verifyToken("verified-firebase-token")).thenReturn(registrationToken);
        when(userRepository.existsByFirebaseUid("firebase_uid_123")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashedPassword123");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtUtil.generateAccessToken(anyString(), anyMap())).thenReturn("mock.access.token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("mock.refresh.token");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock.access.token", response.getAccessToken());
        assertEquals("mock.refresh.token", response.getRefreshToken());
        assertEquals("test@mediwise.com", response.getUser().getEmail());
        assertEquals("John Doe", response.getUser().getFullName());
        assertEquals(User.Role.PATIENT, response.getUser().getRole());

        verify(userRepository).save(any(User.class));
        verify(patientProfileRepository).save(any(PatientProfile.class));
    }

    @Test
    @DisplayName("Should successfully register new doctor and create Doctor record")
    void testRegisterDoctorSuccess() {
        User doctorUser = User.builder()
                .id(UUID.randomUUID())
                .email("doctor@mediwise.com")
                .fullName("Dr. Gregory House")
                .role(User.Role.DOCTOR)
                .active(true)
                .build();

        RegisterRequest request = RegisterRequest.builder()
                .fullName("Dr. Gregory House")
                .email("doctor@mediwise.com")
                .password("doctor123")
                .role(User.Role.DOCTOR)
                .specialty("Diagnostics")
                .licenseNumber("DOC-12345")
                .experienceYears(15)
                .build();

        when(userRepository.existsByEmail("doctor@mediwise.com")).thenReturn(false);
        when(passwordEncoder.encode("doctor123")).thenReturn("hashedDoctor123");
        when(userRepository.save(any(User.class))).thenReturn(doctorUser);
        when(jwtUtil.generateAccessToken(anyString(), anyMap())).thenReturn("mock.access.token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("mock.refresh.token");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals(User.Role.DOCTOR, response.getUser().getRole());
        verify(userRepository).save(any(User.class));
        verify(doctorRepository).save(any(com.mediwise.doctor.model.Doctor.class));
    }

    @Test
    @DisplayName("Should reject registration if role is ADMIN")
    void testRegisterAdminFails() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("Malicious Actor")
                .email("hacker@example.com")
                .password("password123")
                .role(User.Role.ADMIN)
                .build();

        when(userRepository.existsByEmail("hacker@example.com")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals("FORBIDDEN_ROLE", ex.getCode());
    }

    @Test
    @DisplayName("Should fail registration if email is already taken")
    void testRegisterDuplicateEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@mediwise.com")
                .password("secret123")
                .build();

        when(userRepository.existsByEmail("test@mediwise.com")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals("EMAIL_TAKEN", exception.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully login with email and password")
    void testLoginWithEmailAndPasswordSuccess() {
        LoginRequest request = LoginRequest.builder()
                .emailOrPhone("test@mediwise.com")
                .password("secret123")
                .build();

        when(userRepository.findByIdentifier("test@mediwise.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("secret123", "hashedPassword123")).thenReturn(true);
        when(jwtUtil.generateAccessToken(anyString(), anyMap())).thenReturn("mock.access.token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("mock.refresh.token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock.access.token", response.getAccessToken());
        assertEquals("mock.refresh.token", response.getRefreshToken());
        assertEquals("test@mediwise.com", response.getUser().getEmail());
    }

    @Test
    @DisplayName("Should reject incorrect direct credentials")
    void testLoginWithWrongPassword() {
        LoginRequest request = LoginRequest.builder()
                .emailOrPhone("test@mediwise.com")
                .password("wrongpassword")
                .build();

        when(userRepository.findByIdentifier("test@mediwise.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrongpassword", "hashedPassword123")).thenReturn(false);

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> authService.login(request));
        // Deliberately generic — must not reveal whether the account exists (enumeration).
        assertEquals("Invalid email/phone or password.", exception.getMessage());
    }

    @Test
    @DisplayName("Should successfully login with Firebase ID token")
    void testLoginWithFirebaseToken() {
        LoginRequest request = LoginRequest.builder()
                .firebaseIdToken("mock_google_token_123")
                .build();

        FirebaseToken firebaseToken = mock(FirebaseToken.class);
        when(firebaseToken.getUid()).thenReturn("firebase_uid_123");
        when(firebaseTokenVerifier.verifyToken("mock_google_token_123")).thenReturn(firebaseToken);
        when(firebaseTokenVerifier.extractEmail(firebaseToken)).thenReturn("test@mediwise.com");
        when(userRepository.findByFirebaseUid("firebase_uid_123")).thenReturn(Optional.of(sampleUser));
        when(jwtUtil.generateAccessToken(anyString(), anyMap())).thenReturn("mock.access.token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("mock.refresh.token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock.access.token", response.getAccessToken());
        assertEquals(sampleUser.getEmail(), response.getUser().getEmail());
    }

    @Test
    @DisplayName("Should auto-register new user on first-time Google sign-in")
    void testLoginGoogleSignInAutoRegister() {
        LoginRequest request = LoginRequest.builder()
                .firebaseIdToken("google_id_token")
                .build();

        FirebaseToken firebaseToken = mock(FirebaseToken.class);
        when(firebaseToken.getUid()).thenReturn("google_uid_999");
        when(firebaseTokenVerifier.verifyToken("google_id_token")).thenReturn(firebaseToken);
        when(firebaseTokenVerifier.extractEmail(firebaseToken)).thenReturn("newgoogle@mediwise.com");
        when(firebaseTokenVerifier.extractName(firebaseToken)).thenReturn("Google User");
        when(firebaseTokenVerifier.extractPicture(firebaseToken)).thenReturn("https://avatar.com/photo.jpg");
        when(firebaseTokenVerifier.extractPhone(firebaseToken)).thenReturn("+919999988888");

        when(userRepository.findByFirebaseUid("google_uid_999")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("newgoogle@mediwise.com")).thenReturn(Optional.empty());

        User createdUser = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("google_uid_999")
                .email("newgoogle@mediwise.com")
                .fullName("Google User")
                .role(User.Role.PATIENT)
                .active(true)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(createdUser);
        when(jwtUtil.generateAccessToken(anyString(), anyMap())).thenReturn("mock.access.token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("mock.refresh.token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("newgoogle@mediwise.com", response.getUser().getEmail());
        verify(userRepository).save(any(User.class));
        verify(patientProfileRepository).save(any(PatientProfile.class));
    }

    @Test
    @DisplayName("Should successfully change password for user")
    void testChangePasswordSuccess() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("secret123")
                .newPassword("newSecret456")
                .build();

        when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("secret123", "hashedPassword123")).thenReturn(true);
        when(passwordEncoder.encode("newSecret456")).thenReturn("hashedNewSecret456");

        authService.changePassword(sampleUserId, request);

        verify(userRepository).save(sampleUser);
        assertEquals("hashedNewSecret456", sampleUser.getPasswordHash());
    }

    @Test
    @DisplayName("Should successfully reset password")
    void testResetPasswordSuccess() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .emailOrPhone("test@mediwise.com")
                .token("123456")
                .newPassword("newPassword456")
                .build();

        when(userRepository.findByIdentifier("test@mediwise.com")).thenReturn(Optional.of(sampleUser));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("pwreset:attempts:test@mediwise.com")).thenReturn(null);
        when(valueOperations.get("pwreset:otp:test@mediwise.com")).thenReturn("123456");
        when(passwordEncoder.encode("newPassword456")).thenReturn("newHashedPassword");

        authService.resetPassword(request);

        verify(passwordEncoder).encode("newPassword456");
        verify(userRepository).save(sampleUser);
        assertEquals("newHashedPassword", sampleUser.getPasswordHash());
    }

    @Test
    @DisplayName("Should return MediWise app config for splash screen")
    void testGetAppConfig() {
        AppConfigResponse config = authService.getAppConfig();

        assertNotNull(config);
        assertEquals("MediWise", config.getAppName());
        assertEquals("1.0.0", config.getVersion());
        assertEquals("HEALTHY", config.getStatus());
        assertTrue(config.getFeatures().contains("auth_email_password"));
    }
}
