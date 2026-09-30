package com.mediwise.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The single message type routed through the WebSocket STOMP broker
 * for all WebRTC signaling events.
 *
 * WebRTC signaling has 5 event types (INITIATE, OFFER, ANSWER, ICE, HANGUP).
 * Rather than 5 separate DTO classes, this uses one envelope with a "type"
 * discriminator and an untyped "payload" field, keeping the STOMP message
 * format consistent and letting the Android/Web client use a single message
 * handler.
 *
 * Sent over a WebSocket STOMP connection, not Retrofit HTTP. The roomId is
 * the appointmentId — it uniquely identifies a call session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSignalMessage {

    /** Which signal this envelope carries */
    private SignalType type;

    /**
     * The appointment ID that identifies this call room.
     * Both participants must be on the same appointmentId to connect.
     */
    private String roomId;

    /** User ID of the person sending this signal */
    private String senderId;

    /** User ID of the intended recipient */
    private String recipientId;

    /**
     * The signal payload. Contents differ by type:
     *
     * CALL_INITIATE → null (no payload needed, just a notification)
     * CALL_OFFER → JSON-stringified RTCSessionDescription { type:"offer", sdp:"..."
     * }
     * CALL_ANSWER → JSON-stringified RTCSessionDescription { type:"answer",
     * sdp:"..." }
     * ICE_CANDIDATE → JSON-stringified RTCIceCandidate { candidate:"...",
     * sdpMid:"...", sdpMLineIndex:0 }
     * CALL_HANGUP → null (no payload needed)
     * CALL_REJECTED → optional string reason
     */
    private String payload;

    public enum SignalType {
        /** Step 1: Caller notifies callee that a call is starting */
        CALL_INITIATE,

        /**
         * Step 2: Caller sends their SDP offer (audio/video capabilities description)
         */
        CALL_OFFER,

        /** Step 3: Callee responds with their SDP answer (accepted capabilities) */
        CALL_ANSWER,

        /**
         * Step 4 (multiple): Both sides exchange ICE candidates (network path options)
         */
        ICE_CANDIDATE,

        /** Step 5: Either party ends the call */
        CALL_HANGUP,

        /** Callee rejected the call (busy / declined) */
        CALL_REJECTED,

        /** Media negotiation re-offer (e.g. screen sharing toggle) */
        CALL_RENEGOTIATE
    }
}
