package com.mediwise.auth.service;

import com.mediwise.auth.dto.*;
import com.mediwise.auth.model.User;
import com.mediwise.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import com.mediwise.auth.security.FirebaseTokenVerifier;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.common.exception.UnauthorizedException;
import com.mediwise.common.util.JwtUtil;
import com.mediwise.doctor.model.Doctor;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.repository.PatientProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorRepository doctorRepository;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private static final long REFRESH_EXPIRY_DAYS = 7;
    private static final long ACCESS_EXPIRY_SECONDS = 900;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("EMAIL_TAKEN", "This email is already associated with an account.");
        }

        if (request.getPhone() != null && !request.getPhone().isBlank()
                && userRepository.existsByPhone(request.getPhone().trim())) {
            throw new BusinessException("PHONE_TAKEN", "This phone number is already associated with an account.");
        }

        String firebaseUid = null;
        if (request.getFirebaseIdToken() != null && !request.getFirebaseIdToken().isBlank()) {
            var firebaseToken = firebaseTokenVerifier.verifyToken(request.getFirebaseIdToken());
            firebaseUid = firebaseToken.getUid();
            if (userRepository.existsByFirebaseUid(firebaseUid)) {
                throw new BusinessException("ALREADY_REGISTERED", "An account with this Firebase identity already exists.");
            }
        }

        String passwordHash = null;
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getPassword().length() < 6) {
                throw new BusinessException("INVALID_PASSWORD", "Password must be at least 6 characters.");
            }
            passwordHash = passwordEncoder.encode(request.getPassword());
        } else if (firebaseUid == null) {
            throw new BusinessException("PASSWORD_REQUIRED", "Password is required when not using Firebase authentication.");
        }

        if (request.getRole() == User.Role.ADMIN) {
            throw new BusinessException("FORBIDDEN_ROLE", "Admin accounts cannot be registered publicly.");
        }

        User user = User.builder()
                .firebaseUid(firebaseUid)
                .email(email)
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .fullName(request.getFullName() != null ? request.getFullName().trim() : null)
                .dob(request.getDateOfBirth())
                .passwordHash(passwordHash)
                .role(request.getRole() != null ? request.getRole() : User.Role.PATIENT)
                .active(true)
                .build();

        user = userRepository.save(user);

        if (user.getRole() == User.Role.PATIENT) {
            PatientProfile profile = PatientProfile.builder()
                    .userId(user.getId())
                    .fullName(user.getFullName())
                    .dob(user.getDob())
                    .build();
            patientProfileRepository.save(profile);
        } else if (user.getRole() == User.Role.DOCTOR) {
            String specialty = (request.getSpecialty() != null && !request.getSpecialty().isBlank())
                    ? request.getSpecialty().trim() : "General Medicine";
            Doctor doctor = Doctor.builder()
                    .userId(user.getId())
                    .fullName(user.getFullName() != null ? user.getFullName() : "Dr. " + email)
                    .specialty(specialty)
                    .licenseNumber(request.getLicenseNumber() != null ? request.getLicenseNumber().trim() : null)
                    .experienceYears(request.getExperienceYears() != null ? request.getExperienceYears() : 0)
                    .consultationFee(request.getConsultationFee() != null ? request.getConsultationFee() : BigDecimal.valueOf(500))
                    .bio(request.getBio())
                    .available(true)
                    .verified(false)
                    .specialties(new HashSet<>(List.of(specialty)))
                    .build();
            doctorRepository.save(doctor);
        }

        log.info("New user registered successfully: {} [{}]", user.getEmail(), user.getRole());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user;

        if (request.getFirebaseIdToken() != null && !request.getFirebaseIdToken().isBlank()) {
            var firebaseToken = firebaseTokenVerifier.verifyToken(request.getFirebaseIdToken());
            String firebaseUid = firebaseToken.getUid();
            String firebaseEmail = firebaseTokenVerifier.extractEmail(firebaseToken);
            String firebaseName = firebaseTokenVerifier.extractName(firebaseToken);
            String firebasePicture = firebaseTokenVerifier.extractPicture(firebaseToken);
            String firebasePhone = firebaseTokenVerifier.extractPhone(firebaseToken);
            boolean firebaseEmailVerified = firebaseTokenVerifier.isEmailVerified(firebaseToken);

            var userOpt = userRepository.findByFirebaseUid(firebaseUid);

            if (userOpt.isEmpty() && firebaseEmail != null && !firebaseEmail.isBlank()) {
                var emailMatch = userRepository.findByEmail(firebaseEmail);
                if (emailMatch.isPresent()) {
                    if (!firebaseEmailVerified) {
                        throw new UnauthorizedException(
                                "An account with this email already exists. Please verify your email with your identity provider before signing in this way, or log in with your password instead.");
                    }
                    User existingUser = emailMatch.get();
                    if (existingUser.getFirebaseUid() == null || !existingUser.getFirebaseUid().equals(firebaseUid)) {
                        existingUser.setFirebaseUid(firebaseUid);
                        existingUser = userRepository.save(existingUser);
                    }
                    userOpt = Optional.of(existingUser);
                }
            }

            if (userOpt.isPresent()) {
                user = userOpt.get();
            } else {
                if (firebaseEmail == null || firebaseEmail.isBlank()) {
                    throw new UnauthorizedException("Firebase token does not contain a valid email address.");
                }
                user = User.builder()
                        .firebaseUid(firebaseUid)
                        .email(firebaseEmail)
                        .fullName(firebaseName != null ? firebaseName : "User")
                        .phone(firebasePhone)
                        .role(User.Role.PATIENT)
                        .active(true)
                        .build();
                user = userRepository.save(user);

                PatientProfile profile = PatientProfile.builder()
                        .userId(user.getId())
                        .fullName(user.getFullName())
                        .profileImage(firebasePicture)
                        .build();
                patientProfileRepository.save(profile);
                log.info("Auto-registered new patient account via Google/Firebase login: {}", user.getEmail());
            }

        } else if (request.getEmailOrPhone() != null && !request.getEmailOrPhone().isBlank()
                && request.getPassword() != null && !request.getPassword().isBlank()) {
            user = userRepository.findByIdentifier(request.getEmailOrPhone().trim())
                    .orElseThrow(() -> new UnauthorizedException("No account found with this email or phone."));

            if (user.getPasswordHash() == null) {
                throw new UnauthorizedException(
                        "This account was created via social login. Please sign in with Google/Firebase.");
            }
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                throw new UnauthorizedException("Incorrect password. Please try again.");
            }
        } else {
            throw new UnauthorizedException("Please provide either a Firebase ID token or email/phone + password.");
        }

        if (!user.isActive()) {
            throw new UnauthorizedException("Your account has been suspended. Contact MediWise support.");
        }

        log.info("User logged in successfully: {} [{}]", user.getEmail(), user.getRole());
        return buildAuthResponse(user);
    }

    public AuthResponse refresh(String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken)) {
            throw new UnauthorizedException("Invalid or expired refresh token. Please log in again.");
        }

        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw new UnauthorizedException("Provided token is not a refresh token.");
        }

        String jti = jwtUtil.extractJti(refreshToken);
        if (redisTemplate != null && jti != null) {
            Boolean isBlacklisted = (Boolean) redisTemplate.opsForValue().get("blacklist:" + jti);
            if (Boolean.TRUE.equals(isBlacklisted)) {
                throw new UnauthorizedException("Token has been invalidated.");
            }
        }

        String userId = jwtUtil.extractSubject(refreshToken);
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!user.isActive()) {
            throw new UnauthorizedException("Your account has been suspended.");
        }

        // Rotation: blacklist the OLD refresh token now that a new pair is being issued,
        // so it can never be reused even if it was copied/stolen.
        if (redisTemplate != null && jti != null) {
            Date expiration = jwtUtil.extractExpiration(refreshToken);
            long remaining = expiration != null ? expiration.getTime() - System.currentTimeMillis() : 0;
            if (remaining > 0) {
                redisTemplate.opsForValue().set("blacklist:" + jti, true, Duration.ofMillis(remaining));
            }
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String identifier = request.getEmailOrPhone().trim();
        User user = userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email or phone number."));

        log.info("Password reset requested for user: {}", user.getEmail());
        // In production: send email/SMS with reset code or token.
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String identifier = request.getEmailOrPhone().trim();
        User user = userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email or phone number."));

        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new BusinessException("INVALID_PASSWORD", "Password must be at least 6 characters.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password successfully reset for user: {}", user.getEmail());
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
                throw new BusinessException("INVALID_PASSWORD", "Current password does not match.");
            }
        }

        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new BusinessException("INVALID_PASSWORD", "New password must be at least 6 characters.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed successfully for user: {}", user.getEmail());
    }

    public AuthResponse.UserInfo getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return buildUserInfo(user);
    }

    public AppConfigResponse getAppConfig() {
        return AppConfigResponse.builder()
                .appName("MediWise")
                .version("1.0.0")
                .status("HEALTHY")
                .environment(activeProfile)
                .features(List.of(
                        "auth_email_password",
                        "auth_firebase_google",
                        "ai_triage",
                        "video_consultation",
                        "realtime_chat",
                        "doctor_appointments",
                        "razorpay_payments"))
                .build();
    }

    public void logout(String accessToken) {
        if (jwtUtil.isTokenValid(accessToken)) {
            String jti = jwtUtil.extractJti(accessToken);
            Date expiration = jwtUtil.extractExpiration(accessToken);
            long remaining = expiration != null ? expiration.getTime() - System.currentTimeMillis() : 0;
            if (remaining > 0 && redisTemplate != null && jti != null) {
                redisTemplate.opsForValue().set("blacklist:" + jti, true, Duration.ofMillis(remaining));
            }
        }
        SecurityContextHolder.clearContext();
    }

    private AuthResponse.UserInfo buildUserInfo(User user) {
        UUID profileId = null;
        Boolean verified = null;
        String specialty = null;
        String profileImage = null;

        if (user.getRole() == User.Role.PATIENT) {
            var patientOpt = patientProfileRepository.findByUserId(user.getId());
            if (patientOpt.isPresent()) {
                var profile = patientOpt.get();
                profileId = profile.getId();
                profileImage = profile.getProfileImage();
            }
        } else if (user.getRole() == User.Role.DOCTOR) {
            var docOpt = doctorRepository.findByUserId(user.getId());
            if (docOpt.isPresent()) {
                var doctor = docOpt.get();
                profileId = doctor.getId();
                verified = doctor.isVerified();
                specialty = doctor.getSpecialty();
                profileImage = doctor.getProfileImage();
            }
        }

        return AuthResponse.UserInfo.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .dob(user.getDob())
                .role(user.getRole())
                .profileId(profileId)
                .verified(verified)
                .specialty(specialty)
                .profileImage(profileImage)
                .build();
    }

    private AuthResponse buildAuthResponse(User user) {
        Map<String, Object> claims = Map.of(
                "role", user.getRole().name(),
                "email", user.getEmail());
        String access = jwtUtil.generateAccessToken(user.getId().toString(), claims);
        String refresh = jwtUtil.generateRefreshToken(user.getId().toString());

        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(
                    "session:" + user.getId(),
                    user.getRole().name(),
                    Duration.ofDays(REFRESH_EXPIRY_DAYS));
        }

        return AuthResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .expiresIn(ACCESS_EXPIRY_SECONDS)
                .user(buildUserInfo(user))
                .build();
    }
}
