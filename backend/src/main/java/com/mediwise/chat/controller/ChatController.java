package com.mediwise.chat.controller;

import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.auth.model.User;
import com.mediwise.auth.repository.UserRepository;
import com.mediwise.chat.dto.ChatMediaUploadResponse;
import com.mediwise.chat.dto.SendMessageRequest;
import com.mediwise.chat.dto.TypingEvent;
import com.mediwise.chat.model.ChatMessage;
import com.mediwise.chat.service.ChatService;
import com.mediwise.chat.util.ChatRateLimiter;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.response.ApiResponse;
import com.mediwise.common.response.PagedResponse;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.profile.repository.PatientProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Real-time chat via WebSocket/STOMP + REST history")
public class ChatController {

    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_MESSAGES_PER_WINDOW = 20;
    private static final int MAX_TYPING_EVENTS_PER_WINDOW = 30;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(10);

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AppointmentRepository appointmentRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final ChatRateLimiter rateLimiter;

    // ── WebSocket STOMP endpoints ──────────────────────────────────────────

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        if (!isRoomParticipant(request.getRoomId(), principal.getName())) {
            log.warn("User {} attempted to send into room {} without access", principal.getName(), request.getRoomId());
            return;
        }
        if (request.getContent() != null && request.getContent().length() > MAX_MESSAGE_LENGTH) {
            log.warn("User {} sent an oversized message ({} chars) to room {} — dropped",
                    principal.getName(), request.getContent().length(), request.getRoomId());
            return;
        }
        if (!rateLimiter.allow("chat.send:" + principal.getName(), MAX_MESSAGES_PER_WINDOW, RATE_LIMIT_WINDOW)) {
            log.warn("User {} exceeded chat send rate limit — message dropped", principal.getName());
            return;
        }
        ChatMessage saved = chatService.saveMessage(request, principal.getName(), "UNKNOWN");
        messagingTemplate.convertAndSend("/topic/chat/" + request.getRoomId(), saved);
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingEvent event, Principal principal) {
        if (!isRoomParticipant(event.getRoomId(), principal.getName())) {
            log.warn("User {} attempted typing event into room {} without access", principal.getName(), event.getRoomId());
            return;
        }
        if (!rateLimiter.allow("chat.typing:" + principal.getName(), MAX_TYPING_EVENTS_PER_WINDOW, RATE_LIMIT_WINDOW)) {
            return;
        }
        event.setSenderId(principal.getName());
        messagingTemplate.convertAndSend("/topic/chat/" + event.getRoomId() + "/typing", event);
    }

    @MessageMapping("/chat.read")
    public void markRead(@Payload String roomId, Principal principal) {
        if (!isRoomParticipant(roomId, principal.getName())) {
            log.warn("User {} attempted read-receipt into room {} without access", principal.getName(), roomId);
            return;
        }
        if (!rateLimiter.allow("chat.read:" + principal.getName(), MAX_TYPING_EVENTS_PER_WINDOW, RATE_LIMIT_WINDOW)) {
            return;
        }
        chatService.markAsRead(roomId, principal.getName());
        messagingTemplate.convertAndSend("/topic/chat/" + roomId + "/read", principal.getName());
    }

    // ── REST endpoint for message history ─────────────────────────────────

    @GetMapping("/api/v1/chat/{roomId}/messages")
    @Operation(summary = "Get paginated message history for a chat room")
    public ResponseEntity<ApiResponse<PagedResponse<ChatMessage>>> getMessages(
            @PathVariable String roomId,
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        if (!isRoomParticipant(roomId, user.getId().toString())) {
            throw new BusinessException("FORBIDDEN", "You do not have access to this chat room.");
        }

        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(chatService.getMessages(roomId, page, size))));
    }

    @PostMapping(value = "/api/v1/chat/{roomId}/media", consumes = "multipart/form-data")
    @Operation(summary = "Upload a chat attachment (image/PDF/document) and get its URL")
    public ResponseEntity<ApiResponse<ChatMediaUploadResponse>> uploadMedia(
            @PathVariable String roomId,
            @AuthenticationPrincipal User user,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {

        if (!isRoomParticipant(roomId, user.getId().toString())) {
            throw new BusinessException("FORBIDDEN", "You do not have access to this chat room.");
        }

        return ResponseEntity.ok(ApiResponse.success(chatService.uploadMedia(roomId, user.getId().toString(), file)));
    }

    // ── Private helper: is this user actually a participant of this room? ──

    private boolean isRoomParticipant(String roomId, String userIdStr) {
        UUID appointmentId = parseAppointmentId(roomId);
        if (appointmentId == null || userIdStr == null) {
            return false;
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            return false;
        }

        // Admins may observe/support any room for oversight purposes.
        boolean isAdmin = userRepository.findById(userId)
                .map(u -> u.getRole() == User.Role.ADMIN)
                .orElse(false);
        if (isAdmin) {
            return true;
        }

        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return false;
        }

        boolean isPatient = patientProfileRepository.findByUserId(userId)
                .map(p -> p.getId().equals(appointment.getPatientId()))
                .orElse(false);
        if (isPatient) {
            return true;
        }

        return doctorRepository.findByUserId(userId)
                .map(d -> d.getId().equals(appointment.getDoctorId()))
                .orElse(false);
    }

    private UUID parseAppointmentId(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return null;
        }
        String raw = roomId.startsWith("appointment_") ? roomId.substring("appointment_".length()) : roomId;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}