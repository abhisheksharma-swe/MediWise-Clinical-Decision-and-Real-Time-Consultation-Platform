package com.mediwise.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * AOP aspect that wraps all @Service methods and writes audit entries to MongoDB.
 * Uses @Around so we can capture both success and failure outcomes.
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogRepository auditLogRepository;

    public AuditAspect(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Intercepts all public methods in any class annotated with @Service
     * inside the com.mediwise package.
     */
    @Around("within(@org.springframework.stereotype.Service *) && " +
            "execution(public * com.mediwise..*(..))")
    public Object auditServiceCall(ProceedingJoinPoint pjp) throws Throwable {

        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String action = pjp.getTarget().getClass().getSimpleName() + "." + sig.getName();
        String resourceType = pjp.getTarget().getClass().getSimpleName();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actorId = resolveActorId(auth);
        String actorRole = resolveActorRole(auth);
        String ipHash = resolveIpHash();
        String userAgent = resolveUserAgent();

        AuditLog entry = new AuditLog();
        entry.setActorId(actorId);
        entry.setActorRole(actorRole);
        entry.setAction(action.toUpperCase().replace(".", "_"));
        entry.setResourceType(resourceType);
        entry.setIpHash(ipHash);
        entry.setUserAgent(userAgent);
        entry.setTimestamp(java.time.Instant.now());

        try {
            Object result = pjp.proceed();
            entry.setOutcome("SUCCESS");
            saveAsync(entry);
            return result;
        } catch (Throwable ex) {
            entry.setOutcome("FAILURE");
            entry.setErrorCode(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            saveAsync(entry);
            throw ex;
        }
    }

    // ─── Private Helpers ────────────────────────────────────────────────────────

    private String resolveActorId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        return auth.getName();
    }

    private String resolveActorRole(Authentication auth) {
        if (auth == null) return "ANONYMOUS";
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("UNKNOWN");
    }

    private String resolveIpHash() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
            // Hash the IP — HIPAA compliance: never store raw IPs
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveUserAgent() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            return attrs.getRequest().getHeader("User-Agent");
        } catch (Exception e) {
            return null;
        }
    }

    private void saveAsync(AuditLog entry) {
        try {
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            // Audit failure must NEVER break the main flow
            log.error("Failed to persist audit log entry: {}", ex.getMessage());
        }
    }
}
