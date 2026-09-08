package com.mediwise.chat.controller;

import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.chat.dto.CallSignalMessage;
import com.mediwise.chat.util.ChatRateLimiter;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.profile.repository.PatientProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CallSignalingController {

    // Generous limit — a normal WebRTC negotiation can legitimately burst
    // several ICE candidates in quick succession; this only guards against
    // abuse/loops, not real signaling traffic.
    private static final int MAX_SIGNALS_PER_WINDOW = 60;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(10);

    private final SimpMessagingTemplate messagingTemplate;
    private final AppointmentRepository appointmentRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorRepository doctorRepository;
    private final ChatRateLimiter rateLimiter;

    @MessageMapping("/call/initiate")
    public void initiateCall(@Payload CallSignalMessage message, Principal caller) {
        message.setSenderId(caller.getName());
        message.setType(CallSignalMessage.SignalType.CALL_INITIATE);
        log.info("CALL_INITIATE | room={} | from={} → to={}",
                message.getRoomId(), caller.getName(), message.getRecipientId());
        sendToRecipient(message);
    }

    @MessageMapping("/call/offer")
    public void sendOffer(@Payload CallSignalMessage message, Principal caller) {
        message.setSenderId(caller.getName());
        message.setType(CallSignalMessage.SignalType.CALL_OFFER);
        log.debug("CALL_OFFER | room={} | from={} → to={}",
                message.getRoomId(), caller.getName(), message.getRecipientId());
        sendToRecipient(message);
    }

    @MessageMapping("/call/answer")
    public void sendAnswer(@Payload CallSignalMessage message, Principal caller) {
        message.setSenderId(caller.getName());
        message.setType(CallSignalMessage.SignalType.CALL_ANSWER);
        log.debug("CALL_ANSWER | room={} | from={} → to={}",
                message.getRoomId(), caller.getName(), message.getRecipientId());
        sendToRecipient(message);
    }

    @MessageMapping("/call/ice-candidate")
    public void sendIceCandidate(@Payload CallSignalMessage message, Principal caller) {
        message.setSenderId(caller.getName());
        message.setType(CallSignalMessage.SignalType.ICE_CANDIDATE);
        log.debug("ICE_CANDIDATE | room={} | from={} → to={}",
                message.getRoomId(), caller.getName(), message.getRecipientId());
        sendToRecipient(message);
    }

    @MessageMapping("/call/hangup")
    public void hangup(@Payload CallSignalMessage message, Principal caller) {
        message.setSenderId(caller.getName());
        message.setType(CallSignalMessage.SignalType.CALL_HANGUP);
        log.info("CALL_HANGUP | room={} | initiator={}", message.getRoomId(), caller.getName());
        sendToRecipient(message);
    }

    @MessageMapping("/call/reject")
    public void reject(@Payload CallSignalMessage message, Principal caller) {
        message.setSenderId(caller.getName());
        message.setType(CallSignalMessage.SignalType.CALL_REJECTED);
        log.info("CALL_REJECTED | room={} | rejected by={}", message.getRoomId(), caller.getName());
        sendToRecipient(message);
    }

    @MessageMapping("/call/renegotiate")
    public void renegotiate(@Payload CallSignalMessage message, Principal caller) {
        message.setSenderId(caller.getName());
        message.setType(CallSignalMessage.SignalType.CALL_RENEGOTIATE);
        log.debug("CALL_RENEGOTIATE | room={} | from={}", message.getRoomId(), caller.getName());
        sendToRecipient(message);
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private void sendToRecipient(CallSignalMessage message) {
        if (message.getRecipientId() == null || message.getRecipientId().isBlank()) {
            log.warn("Signal {} has no recipientId — dropping", message.getType());
            return;
        }

        if (!rateLimiter.allow("call.signal:" + message.getSenderId(), MAX_SIGNALS_PER_WINDOW, RATE_LIMIT_WINDOW)) {
            log.warn("User {} exceeded call-signaling rate limit — {} dropped", message.getSenderId(), message.getType());
            return;
        }

        if (!areAppointmentParticipants(message.getRoomId(), message.getSenderId(), message.getRecipientId())) {
            log.warn("Signal {} rejected — sender {} and recipient {} are not both participants of room {}",
                    message.getType(), message.getSenderId(), message.getRecipientId(), message.getRoomId());
            return;
        }

        messagingTemplate.convertAndSendToUser(
                message.getRecipientId(),
                "/queue/call",
                message
        );
        log.info("CALL_DELIVERED | type={} room={} from={} -> to={}",
            message.getType(), message.getRoomId(), message.getSenderId(), message.getRecipientId());
    }

    private boolean areAppointmentParticipants(String roomId, String senderIdStr, String recipientIdStr) {
        UUID appointmentId = parseAppointmentId(roomId);
        if (appointmentId == null) {
            return false;
        }

        UUID senderId;
        UUID recipientId;
        try {
            senderId = UUID.fromString(senderIdStr);
            recipientId = UUID.fromString(recipientIdStr);
        } catch (IllegalArgumentException e) {
            return false;
        }

        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return false;
        }

        UUID patientUserId = patientProfileRepository.findById(appointment.getPatientId())
                .map(p -> p.getUserId())
                .orElse(null);
        UUID doctorUserId = doctorRepository.findById(appointment.getDoctorId())
                .map(d -> d.getUserId())
                .orElse(null);

        if (patientUserId == null || doctorUserId == null) {
            return false;
        }

        Set<UUID> participants = Set.of(patientUserId, doctorUserId);
        return participants.contains(senderId)
                && participants.contains(recipientId)
                && !senderId.equals(recipientId);
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