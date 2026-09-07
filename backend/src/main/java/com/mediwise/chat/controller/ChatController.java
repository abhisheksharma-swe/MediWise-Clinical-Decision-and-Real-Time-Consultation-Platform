package com.mediwise.chat.controller;

import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.auth.model.User;
import com.mediwise.chat.dto.SendMessageRequest;
import com.mediwise.chat.dto.TypingEvent;
import com.mediwise.chat.model.ChatMessage;
import com.mediwise.chat.service.ChatService;
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
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Real-time chat via WebSocket/STOMP + REST history")
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AppointmentRepository appointmentRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorRepository doctorRepository;

    // ── WebSocket STOMP endpoints ──────────────────────────────────────────

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        if (!isRoomParticipant(request.getRoomId(), principal.getName())) {
            log.warn("User {} attempted to send into room {} without access", principal.getName(), request.getRoomId());
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
        event.setSenderId(principal.getName());
        messagingTemplate.convertAndSend("/topic/chat/" + event.getRoomId() + "/typing", event);
    }

    @MessageMapping("/chat.read")
    public void markRead(@Payload String roomId, Principal principal) {
        if (!isRoomParticipant(roomId, principal.getName())) {
            log.warn("User {} attempted read-receipt into room {} without access", principal.getName(), roomId);
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