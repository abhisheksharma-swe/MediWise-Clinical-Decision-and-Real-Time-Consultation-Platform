package com.mediwise.auth.security;

import com.google.firebase.auth.FirebaseToken;
import com.mediwise.auth.model.User;
import com.mediwise.auth.repository.UserRepository;
import com.mediwise.common.util.JwtUtil;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.repository.PatientProfileRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final UserRepository userRepository;
    private final PatientProfileRepository patientProfileRepository;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Try verifying as MediWise internal JWT token
        if (jwtUtil.isTokenValid(token)) {
            try {
                String jti = jwtUtil.extractJti(token);
                if (redisTemplate != null && jti != null) {
                    Boolean isBlacklisted = (Boolean) redisTemplate.opsForValue().get("blacklist:" + jti);
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        log.debug("Token with JTI {} is blacklisted", jti);
                        filterChain.doFilter(request, response);
                        return;
                    }
                }

                String subject = jwtUtil.extractSubject(token);
                if (subject != null) {
                    UUID userId = UUID.fromString(subject);
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null && user.isActive()) {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                        );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        filterChain.doFilter(request, response);
                        return;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed processing MediWise JWT token: {}", e.getMessage());
            }
        }

        // Fallback: Check if token is a direct Firebase ID token
        try {
            FirebaseToken firebaseToken = firebaseTokenVerifier.verifyToken(token);
            String uid = firebaseToken.getUid();
            User user = userRepository.findByFirebaseUid(uid).orElse(null);

            // If not found by UID, check by email and link
            if (user == null) {
                String email = firebaseTokenVerifier.extractEmail(firebaseToken);
                if (email != null && !email.isBlank()) {
                    user = userRepository.findByEmail(email).map(existingUser -> {
                        existingUser.setFirebaseUid(uid);
                        return userRepository.save(existingUser);
                    }).orElse(null);
                }
            }

            // If user still doesn't exist, auto-provision
            if (user == null) {
                String email = firebaseTokenVerifier.extractEmail(firebaseToken);
                if (email != null && !email.isBlank()) {
                    String name = firebaseTokenVerifier.extractName(firebaseToken);
                    String phone = firebaseTokenVerifier.extractPhone(firebaseToken);
                    String picture = firebaseTokenVerifier.extractPicture(firebaseToken);

                    user = User.builder()
                            .firebaseUid(uid)
                            .email(email)
                            .fullName(name != null ? name : "User")
                            .phone(phone)
                            .role(User.Role.PATIENT)
                            .active(true)
                            .build();
                    user = userRepository.save(user);

                    PatientProfile profile = PatientProfile.builder()
                            .userId(user.getId())
                            .fullName(user.getFullName())
                            .profileImage(picture)
                            .build();
                    patientProfileRepository.save(profile);
                    log.info("Auto-provisioned patient profile from Firebase token for {}", email);
                }
            }

            if (user != null && user.isActive()) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.debug("Rejected Bearer token as Firebase token: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
