package com.mediwise.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.domain.model.CallMediaType
import com.mediwise.domain.model.CallSignalType
import com.mediwise.domain.model.IncomingCall
import com.mediwise.domain.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-wide "is a call ringing right now" listener, hoisted at the [NavGraph] composable
 * (same pattern as [RoleViewModel]) so an incoming call can be caught and shown regardless
 * of which screen the user is currently on - not just from a dedicated call screen.
 *
 * Deliberately separate from the per-call [com.mediwise.presentation.screens.call.CallViewModel]:
 * this one only tracks *whether* a call is ringing (for the incoming-call UI/notification);
 * the WebRTC session itself is owned by CallViewModel once the user navigates to CallScreen.
 * Both independently observe [CallRepository.observeSignals] - the underlying STOMP
 * subscription is shared/multicast per destination, so this doesn't double-connect.
 */
@HiltViewModel
class IncomingCallViewModel @Inject constructor(
    private val callRepository: CallRepository,
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val _incomingCall = MutableStateFlow<IncomingCall?>(null)
    val incomingCall: StateFlow<IncomingCall?> = _incomingCall.asStateFlow()

    init {
        viewModelScope.launch {
            val myId = sessionDataStore.userId.filter { !it.isNullOrBlank() }.first()
            android.util.Log.d("IncomingCall", "CALL_LISTENER_READY userId=$myId")

            callRepository.observeSignals().collect { signal ->
                when (signal.type) {
                    CallSignalType.CALL_INITIATE -> {
                        val current = _incomingCall.value
                        if (current == null) {
                            val mediaType = CallMediaType.entries.firstOrNull { it.name == signal.payload } ?: CallMediaType.AUDIO
                            _incomingCall.value = IncomingCall(roomId = signal.roomId, callerId = signal.senderId, mediaType = mediaType)
                        } else if (current.roomId != signal.roomId) {
                            // Already ringing/on a call elsewhere - a second simultaneous call is auto-declined.
                            callRepository.sendReject(signal.roomId, signal.senderId)
                        }
                    }
                    CallSignalType.CALL_HANGUP, CallSignalType.CALL_REJECTED -> {
                        // The caller cancelled before this device answered.
                        if (_incomingCall.value?.roomId == signal.roomId) {
                            _incomingCall.value = null
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    /** Called once the ringing call has been accepted (navigated to CallScreen) or declined. */
    fun clearIncomingCall() {
        _incomingCall.value = null
    }
}
