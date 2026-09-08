package com.mediwise.data.repository

import android.util.Log
import com.mediwise.BuildConfig
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.core.network.StompClient
import com.mediwise.data.remote.dto.CallSignalDto
import com.mediwise.domain.model.CallMediaType
import com.mediwise.domain.model.CallSignal
import com.mediwise.domain.model.CallSignalType
import com.mediwise.domain.repository.CallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallRepositoryImpl @Inject constructor(
    private val stompClient: StompClient,
    private val sessionDataStore: SessionDataStore
) : CallRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // IncomingCallViewModel starts collecting the moment the app opens (app-wide, session-
    // long), but a per-call CallViewModel only starts collecting once the user taps Accept -
    // by then the caller may have already sent CALL_OFFER. A plain pass-through Flow (replay
    // = 0, as StompClient.subscribe returns) would silently drop that offer for the late
    // subscriber. This small replay buffer exists purely to close that race; CallViewModel
    // still gates every signal by roomId/expected-state so a stray replayed signal from an
    // older call attempt on the same room is a no-op rather than a misfire.
    private val replaySignals = MutableSharedFlow<CallSignal>(replay = 5, extraBufferCapacity = 16)
    private var forwardingStarted = false

    override fun observeSignals(): Flow<CallSignal> {
        ensureForwarding()
        return replaySignals
    }

    private fun ensureForwarding() {
        // Idempotent - a no-op if chat (or a prior call) already connected this same
        // singleton StompClient. Call signaling needs the connection alive for the whole
        // session, not just while a specific screen is open, unlike chat's per-room connect.
        stompClient.connect(BuildConfig.WS_URL) { sessionDataStore.accessToken.first() }
        Log.d(TAG, "Call signaling forwarding initialized ws=${BuildConfig.WS_URL}")
        synchronized(this) {
            if (forwardingStarted) return
            forwardingStarted = true
        }
        scope.launch {
            stompClient.subscribe("/user/queue/call").mapNotNull { frame ->
                try {
                    val dto = json.decodeFromString(CallSignalDto.serializer(), frame.body)
                    CallSignal(
                        type = CallSignalType.valueOf(dto.type),
                        roomId = dto.roomId,
                        senderId = dto.senderId,
                        recipientId = dto.recipientId,
                        payload = dto.payload
                    )
                } catch (e: Exception) {
                    null
                }
            }.collect { signal ->
                Log.d(TAG, "Received call signal type=${signal.type} room=${signal.roomId} from=${signal.senderId}")
                replaySignals.emit(signal)
            }
        }
    }

    override fun sendInitiate(roomId: String, recipientId: String, mediaType: CallMediaType) {
        send("/app/call/initiate", CallSignalType.CALL_INITIATE, roomId, recipientId, payload = mediaType.name)
    }

    override fun sendOffer(roomId: String, recipientId: String, sdp: String) {
        send("/app/call/offer", CallSignalType.CALL_OFFER, roomId, recipientId, payload = sdp)
    }

    override fun sendAnswer(roomId: String, recipientId: String, sdp: String) {
        send("/app/call/answer", CallSignalType.CALL_ANSWER, roomId, recipientId, payload = sdp)
    }

    override fun sendIceCandidate(roomId: String, recipientId: String, candidateJson: String) {
        send("/app/call/ice-candidate", CallSignalType.ICE_CANDIDATE, roomId, recipientId, payload = candidateJson)
    }

    override fun sendHangup(roomId: String, recipientId: String) {
        send("/app/call/hangup", CallSignalType.CALL_HANGUP, roomId, recipientId)
    }

    override fun sendReject(roomId: String, recipientId: String) {
        send("/app/call/reject", CallSignalType.CALL_REJECTED, roomId, recipientId)
    }

    override fun sendRenegotiate(roomId: String, recipientId: String, sdp: String) {
        send("/app/call/renegotiate", CallSignalType.CALL_RENEGOTIATE, roomId, recipientId, payload = sdp)
    }

    private fun send(destination: String, type: CallSignalType, roomId: String, recipientId: String, payload: String? = null) {
        try {
            val dto = CallSignalDto(type = type.name, roomId = roomId, recipientId = recipientId, payload = payload)
            Log.d(TAG, "Sending call signal type=$type room=$roomId recipient=$recipientId")
            stompClient.send(destination, json.encodeToString(CallSignalDto.serializer(), dto))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send call signal type=$type room=$roomId", e)
        }
    }

    companion object {
        private const val TAG = "CallRepository"
    }
}
