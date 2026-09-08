package com.mediwise.domain.repository

import com.mediwise.domain.model.CallMediaType
import com.mediwise.domain.model.CallSignal
import kotlinx.coroutines.flow.Flow

/**
 * WebRTC signaling transport, wrapping the same shared [com.mediwise.core.network.StompClient]
 * chat already uses (see ChatRepositoryImpl) — the backend's CallSignalingController is a pure
 * STOMP relay over call endpoints -> `/user/{recipientId}/queue/call`, so there's no REST surface
 * here at all, matching the "reuse the existing infrastructure" constraint on this feature.
 */
interface CallRepository {
    /**
     * Subscribes to this user's private call-signal queue. Safe to call more than once/from
     * multiple collectors - connecting the shared STOMP client is idempotent, and this is
     * meant to be observed once for the whole app session (see IncomingCallViewModel), not
     * per-screen like chat's per-room subscriptions.
     */
    fun observeSignals(): Flow<CallSignal>

    fun sendInitiate(roomId: String, recipientId: String, mediaType: CallMediaType)
    fun sendOffer(roomId: String, recipientId: String, sdp: String)
    fun sendAnswer(roomId: String, recipientId: String, sdp: String)
    fun sendIceCandidate(roomId: String, recipientId: String, candidateJson: String)
    fun sendHangup(roomId: String, recipientId: String)
    fun sendReject(roomId: String, recipientId: String)
    fun sendRenegotiate(roomId: String, recipientId: String, sdp: String)
}
