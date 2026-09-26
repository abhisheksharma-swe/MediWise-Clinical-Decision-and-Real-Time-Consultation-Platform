package com.mediwise.common.audit;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data @Builder
public class AuditLogResponse {
    private String id;
    private String actorId;
    private String actorRole;
    private String action;
    private String resourceType;
    private String resourceId;
    private String outcome;
    private String errorCode;
    private Instant timestamp;

    public static AuditLogResponse from(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .actorId(log.getActorId())
                .actorRole(log.getActorRole())
                .action(log.getAction())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .outcome(log.getOutcome())
                .errorCode(log.getErrorCode())
                .timestamp(log.getTimestamp())
                .build();
    }
}
