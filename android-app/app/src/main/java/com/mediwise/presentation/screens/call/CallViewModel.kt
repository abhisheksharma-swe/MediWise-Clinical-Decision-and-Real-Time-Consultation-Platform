package com.mediwise.presentation.screens.call

import android.util.Log
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.domain.model.CallDirection
import com.mediwise.domain.model.CallMediaType
import com.mediwise.domain.model.CallSignal
import com.mediwise.domain.model.CallSignalType
import com.mediwise.domain.model.CallState
import com.mediwise.domain.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import javax.inject.Inject

data class CallUiState(
    val state: CallState = CallState.IDLE,
    val roomId: String = "",
    val otherPartyId: String = "",
    val direction: CallDirection = CallDirection.OUTGOING,
    val mediaType: CallMediaType = CallMediaType.AUDIO,
    /** False only for the brief window an incoming call is showing Accept/Decline before the
     * user has tapped Accept - WebRTC/the mic aren't touched until this flips true. */
    val hasAccepted: Boolean = true,
    val isMuted: Boolean = false,
    val elapsedSeconds: Int = 0,
    val error: String? = null
)

private const val CALL_TIMEOUT_MS = 45_000L

/**
 * Owns the WebRTC PeerConnection for exactly one call session, from ring/dial through
 * hangup. Signaling goes through [CallRepository] (a thin wrapper over the same
 * StompClient chat uses, talking to the backend's already-built CallSignalingController).
 * There's no existing calling precedent in this app to reuse - this is the new piece.
 */
@HiltViewModel
class CallViewModel @Inject constructor(
    private val callRepository: CallRepository,
    private val sessionDataStore: SessionDataStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private var myUserId: String = ""
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private val pendingRemoteIceCandidates = mutableListOf<IceCandidate>()
    private val eglBase: EglBase = EglBase.create()

    private var signalJob: Job? = null
    private var timeoutJob: Job? = null
    private var timerJob: Job? = null
    private var started = false
    private var cleanedUp = false

    private val audioManager: AudioManager by lazy { appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var audioFocusRequest: AudioFocusRequest? = null
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    /** Renderer surfaces (video, Phase D) reuse this same factory-owned context - exposed so CallScreen can init a SurfaceViewRenderer against the same EGL context. */
    fun eglBaseContext(): EglBase.Context = eglBase.eglBaseContext

    fun startOutgoingCall(roomId: String, recipientId: String, mediaType: CallMediaType) {
        if (started) return
        started = true
        cleanedUp = false
        Log.i(TAG, "CALL_START room=$roomId recipient=$recipientId media=$mediaType")
        Log.d(TAG, "Starting outgoing call room=$roomId recipient=$recipientId media=$mediaType")
        viewModelScope.launch {
            myUserId = sessionDataStore.userId.first().orEmpty()
            _uiState.update {
                it.copy(state = CallState.RINGING, roomId = roomId, otherPartyId = recipientId, direction = CallDirection.OUTGOING, mediaType = mediaType)
            }
            observeSignalsFor(roomId)
            callRepository.sendInitiate(roomId, recipientId, mediaType)
            // There's no distinct "callee accepted" signal type in the backend's vocabulary
            // (see CallSignalMessage.SignalType) - the offer is created and sent right away,
            // and CallRepository's replay buffer covers the callee's CallViewModel starting
            // its collector late (only once they tap Accept).
            createAndSendOffer()
            startRingTimeout()
        }
    }

    /**
     * Shows the incoming-call (Accept/Decline) UI without touching WebRTC or the mic yet -
     * called as soon as CallScreen opens for an incoming call. Listening for signals starts
     * immediately though, so a HANGUP from the caller (they gave up) is still caught while
     * this device is still deciding.
     */
    fun showIncomingRinging(roomId: String, callerId: String, mediaType: CallMediaType) {
        if (_uiState.value.roomId == roomId) return // already showing this call
        Log.d(TAG, "Incoming call room=$roomId caller=$callerId media=$mediaType")
        viewModelScope.launch { myUserId = sessionDataStore.userId.first().orEmpty() }
        _uiState.update {
            it.copy(state = CallState.RINGING, roomId = roomId, otherPartyId = callerId, direction = CallDirection.INCOMING, mediaType = mediaType, hasAccepted = false)
        }
        observeSignalsFor(roomId)
    }

    fun acceptIncomingCall() {
        val state = _uiState.value
        if (started || state.roomId.isBlank()) return
        started = true
        cleanedUp = false
        _uiState.update { it.copy(state = CallState.CONNECTING, hasAccepted = true) }
        ensurePeerConnection(state.roomId, state.otherPartyId)
        // The pre-accept collector (from showIncomingRinging) deliberately ignores CALL_OFFER
        // until hasAccepted flips true - by then it may already have passed the offer emission
        // by, since replay only re-delivers to a *new* subscriber. Restarting the subscription
        // here forces a fresh one, which immediately replays the last few buffered signals
        // (including that offer, almost certainly still within the small replay window) now
        // that this device is actually ready to act on it.
        observeSignalsFor(state.roomId)
    }

    /** Declines without ever creating a PeerConnection - cheaper than accept+hangup and avoids ever touching the mic for a call the user is rejecting. */
    fun rejectIncomingCall() {
        val state = _uiState.value
        if (state.roomId.isNotBlank() && state.otherPartyId.isNotBlank()) {
            callRepository.sendReject(state.roomId, state.otherPartyId)
        }
        _uiState.update { it.copy(state = CallState.ENDED) }
        cleanup()
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        localAudioTrack?.setEnabled(!newMuted)
        _uiState.update { it.copy(isMuted = newMuted) }
    }

    fun endCall() {
        val state = _uiState.value
        if (state.roomId.isNotBlank() && state.otherPartyId.isNotBlank() && state.state !in setOf(CallState.ENDED, CallState.FAILED)) {
            callRepository.sendHangup(state.roomId, state.otherPartyId)
        }
        _uiState.update { it.copy(state = CallState.ENDED) }
        cleanup()
    }

    private fun observeSignalsFor(roomId: String) {
        signalJob?.cancel()
        signalJob = viewModelScope.launch {
            callRepository.observeSignals().collect { signal ->
                if (signal.roomId != roomId) return@collect // not this call
                handleSignal(signal)
            }
        }
    }

    private fun handleSignal(signal: CallSignal) {
        val current = _uiState.value
        Log.d(TAG, "Handling signal type=${signal.type} room=${signal.roomId} state=${current.state}")
        when (signal.type) {
            CallSignalType.CALL_OFFER -> {
                // Only meaningful for the callee, only after they've tapped Accept (never
                // touch WebRTC/the mic while still showing the ringing Accept/Decline UI),
                // and only once - a replayed duplicate is a no-op.
                if (current.direction == CallDirection.INCOMING && current.hasAccepted && peerConnection?.remoteDescription == null) {
                    val sdp = signal.payload ?: return
                    applyRemoteOfferAndAnswer(sdp)
                }
            }
            CallSignalType.CALL_ANSWER -> {
                // Only meaningful for the caller, mid-ringing/connecting.
                if (current.direction == CallDirection.OUTGOING && current.state in setOf(CallState.RINGING, CallState.CONNECTING)) {
                    val sdp = signal.payload ?: return
                    applyRemoteAnswer(sdp)
                }
            }
            CallSignalType.ICE_CANDIDATE -> {
                val candidate = parseIceCandidate(signal.payload ?: return) ?: return
                val pc = peerConnection
                if (pc == null || pc.remoteDescription == null) {
                    pendingRemoteIceCandidates += candidate
                    Log.d(TAG, "ICE_CANDIDATE_RECEIVED room=${_uiState.value.roomId} queued=true")
                } else {
                    pc.addIceCandidate(candidate)
                    Log.d(TAG, "ICE_CANDIDATE_RECEIVED room=${_uiState.value.roomId} queued=false")
                    Log.d(TAG, "ICE_CANDIDATE_ADDED room=${_uiState.value.roomId}")
                }
            }
            CallSignalType.CALL_HANGUP, CallSignalType.CALL_REJECTED -> {
                if (current.state != CallState.ENDED) {
                    _uiState.update { it.copy(state = CallState.ENDED, error = if (signal.type == CallSignalType.CALL_REJECTED) "Call declined" else null) }
                    cleanup()
                }
            }
            CallSignalType.CALL_INITIATE, CallSignalType.CALL_RENEGOTIATE -> Unit
        }
    }

    private fun startRingTimeout() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(CALL_TIMEOUT_MS)
            val current = _uiState.value
            if (current.state == CallState.RINGING) {
                callRepository.sendHangup(current.roomId, current.otherPartyId)
                _uiState.update { it.copy(state = CallState.FAILED, error = "No answer") }
                cleanup()
            }
        }
    }

    // ── WebRTC plumbing ──────────────────────────────────────────────────────

    private fun factory(): PeerConnectionFactory {
        peerConnectionFactory?.let { return it }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        val f = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        peerConnectionFactory = f
        return f
    }

    private fun ensurePeerConnection(roomId: String, otherPartyId: String) {
        if (peerConnection != null) return
        setupAudioForCall()

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val pc = factory().createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "ICE_CANDIDATE_CREATED room=$roomId recipient=$otherPartyId")
                callRepository.sendIceCandidate(roomId, otherPartyId, iceCandidateToJson(candidate))
                Log.d(TAG, "ICE_CANDIDATE_SENT room=$roomId recipient=$otherPartyId")
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        Log.i(TAG, "ICE_CONNECTED room=${_uiState.value.roomId}")
                        _uiState.update { it.copy(state = CallState.CONNECTED) }
                        timeoutJob?.cancel()
                        startElapsedTimer()
                    }
                    PeerConnection.PeerConnectionState.FAILED, PeerConnection.PeerConnectionState.CLOSED -> {
                        Log.e(TAG, "${if (newState == PeerConnection.PeerConnectionState.FAILED) "ICE_FAILED" else "PEER_CONNECTION_CLOSED"} room=${_uiState.value.roomId}")
                        if (_uiState.value.state != CallState.ENDED) {
                            _uiState.update { it.copy(state = CallState.FAILED, error = "Connection lost") }
                            cleanup()
                        }
                    }
                    PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        // WebRTC can recover from a transient DISCONNECTED on its own (brief
                        // network blip) - only treat FAILED as terminal, matching how a real
                        // phone call shows "reconnecting" rather than dropping immediately.
                        _uiState.update { it.copy(error = "Reconnecting...") }
                    }
                    else -> Unit
                }
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE_${state.name} room=${_uiState.value.roomId}")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver, streams: Array<out MediaStream>) {}
        })
        peerConnection = pc

        val audioConstraints = MediaConstraints()
        val source = factory().createAudioSource(audioConstraints)
        localAudioSource = source
        val track = factory().createAudioTrack("audio_${roomId}", source)
        track.setEnabled(!_uiState.value.isMuted)
        localAudioTrack = track
        pc?.addTrack(track, listOf("stream_$roomId"))
    }

    private fun applyRemoteOfferAndAnswer(sdp: String) {
        val state = _uiState.value
        ensurePeerConnection(state.roomId, state.otherPartyId)
        val pc = peerConnection ?: return
        val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                flushPendingRemoteIceCandidates()
                val constraints = MediaConstraints()
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answer: SessionDescription) {
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                Log.i(TAG, "ANSWER_CREATED room=${state.roomId} recipient=${state.otherPartyId}")
                                callRepository.sendAnswer(state.roomId, state.otherPartyId, answer.description)
                                Log.i(TAG, "ANSWER_SENT room=${state.roomId} recipient=${state.otherPartyId}")
                                _uiState.update { it.copy(state = CallState.CONNECTING) }
                            }
                            override fun onSetFailure(error: String?) { onWebRtcError(error) }
                            override fun onCreateSuccess(sdp: SessionDescription?) {}
                            override fun onCreateFailure(error: String?) {}
                        }, answer)
                    }
                    override fun onCreateFailure(error: String?) { onWebRtcError(error) }
                    override fun onSetSuccess() {}
                    override fun onSetFailure(error: String?) {}
                }, constraints)
            }
            override fun onSetFailure(error: String?) { onWebRtcError(error) }
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, offer)
    }

    /** Called by the caller once it's ready to dial - creates the PeerConnection + local offer and sends it. */
    private fun createAndSendOffer() {
        val state = _uiState.value
        ensurePeerConnection(state.roomId, state.otherPartyId)
        val pc = peerConnection ?: return
        val constraints = MediaConstraints()
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(offer: SessionDescription) {
                Log.i(TAG, "OFFER_CREATED room=${state.roomId} recipient=${state.otherPartyId}")
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        callRepository.sendOffer(state.roomId, state.otherPartyId, offer.description)
                        Log.i(TAG, "OFFER_SENT room=${state.roomId} recipient=${state.otherPartyId}")
                    }
                    override fun onSetFailure(error: String?) { onWebRtcError(error) }
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                }, offer)
            }
            override fun onCreateFailure(error: String?) { onWebRtcError(error) }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun applyRemoteAnswer(sdp: String) {
        val pc = peerConnection ?: return
        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.i(TAG, "ANSWER_RECEIVED room=${_uiState.value.roomId}")
                flushPendingRemoteIceCandidates()
                _uiState.update { it.copy(state = CallState.CONNECTING) }
            }
            override fun onSetFailure(error: String?) { onWebRtcError(error) }
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, answer)
    }

    private fun onWebRtcError(message: String?) {
        _uiState.update { it.copy(state = CallState.FAILED, error = message ?: "Call setup failed") }
        cleanup()
    }

    private fun iceCandidateToJson(candidate: IceCandidate): String {
        return JSONObject().apply {
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("candidate", candidate.sdp)
        }.toString()
    }

    private fun parseIceCandidate(json: String): IceCandidate? {
        return try {
            val obj = JSONObject(json)
            IceCandidate(obj.optString("sdpMid"), obj.optInt("sdpMLineIndex"), obj.optString("candidate"))
        } catch (e: Exception) {
            null
        }
    }

    private fun flushPendingRemoteIceCandidates() {
        val pc = peerConnection ?: return
        if (pc.remoteDescription == null || pendingRemoteIceCandidates.isEmpty()) return
        pendingRemoteIceCandidates.toList().forEach { pc.addIceCandidate(it) }
        Log.d(TAG, "ICE_CANDIDATE_ADDED room=${_uiState.value.roomId} count=${pendingRemoteIceCandidates.size}")
        pendingRemoteIceCandidates.clear()
    }

    private fun startElapsedTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    // ── Audio focus / routing ────────────────────────────────────────────────

    private fun setupAudioForCall() {
        previousAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .build()
        audioManager.requestAudioFocus(request)
        audioFocusRequest = request
    }

    private fun releaseAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
        audioManager.mode = previousAudioMode
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────

    private fun cleanup() {
        if (cleanedUp) return
        cleanedUp = true
        Log.i(TAG, "PEER_CONNECTION_CLOSED room=${_uiState.value.roomId}")
        timeoutJob?.cancel()
        timerJob?.cancel()
        signalJob?.cancel()
        localAudioTrack?.setEnabled(false)
        localAudioTrack?.dispose()
        localAudioTrack = null
        localAudioSource?.dispose()
        localAudioSource = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        pendingRemoteIceCandidates.clear()
        releaseAudioFocus()
        started = false
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        eglBase.release()
    }

    companion object {
        private const val TAG = "CallViewModel"
    }
}
