package com.mediwise.domain.model

/**
 * Mirrors the backend's `CallSignalMessage`/`SignalType` (chat/dto/CallSignalMessage.java) —
 * the single envelope type routed through the call endpoints and `/user/queue/call` for all
 * WebRTC signaling. `roomId` is the appointment id (`"appointment_<id>"`), same convention
 * chat already uses.
 */
data class CallSignal(
    val type: CallSignalType,
    val roomId: String,
    val senderId: String,
    val recipientId: String,
    /** JSON-stringified RTCSessionDescription (OFFER/ANSWER), RTCIceCandidate (ICE_CANDIDATE), or a plain reason string (REJECTED) — null for INITIATE/HANGUP. */
    val payload: String? = null
)

enum class CallSignalType {
    CALL_INITIATE, CALL_OFFER, CALL_ANSWER, ICE_CANDIDATE, CALL_HANGUP, CALL_REJECTED, CALL_RENEGOTIATE
}

enum class CallMediaType { AUDIO, VIDEO }

enum class CallDirection { INCOMING, OUTGOING }

enum class CallState {
    IDLE, RINGING, CONNECTING, CONNECTED, ENDED, FAILED
}

/** What the global incoming-call listener hands to the UI to know a call is ringing. */
data class IncomingCall(
    val roomId: String,
    val callerId: String,
    val mediaType: CallMediaType
)
