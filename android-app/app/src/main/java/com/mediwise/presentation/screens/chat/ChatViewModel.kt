package com.mediwise.presentation.screens.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.core.result.Result
import com.mediwise.core.result.onSuccess
import com.mediwise.domain.model.ChatMessage
import com.mediwise.domain.model.ConnectionStatus
import com.mediwise.domain.model.Role
import com.mediwise.domain.repository.AppointmentRepository
import com.mediwise.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    /** Chronological order, oldest first — matches how a live STOMP message is appended at the end. */
    val messages: List<ChatMessage> = emptyList(),
    val otherPartyName: String = "",
    val otherPartyUserId: String = "",
    val canCall: Boolean = false,
    val myUserId: String = "",
    val isOtherPartyTyping: Boolean = false,
    val isLoadingHistory: Boolean = false,
    /** True while an older page is being fetched for scroll-up pagination. */
    val isLoadingOlderMessages: Boolean = false,
    /** False once a page comes back with fewer messages than requested — nothing older to load. */
    val hasMoreHistory: Boolean = true,
    /** True while a message is in flight - the send button shows a small progress indicator instead of a chat bubble. */
    val isSending: Boolean = false,
    /** The text of the last message that failed to send, kept so the UI can offer a one-tap retry. */
    val failedMessage: String? = null,
    /** True while an attachment is uploading, before it's sent as a message - the attach button shows a spinner instead of allowing another pick. */
    val isUploadingAttachment: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    /** True once the other participant has read up to (at least) this device's latest message. */
    val otherPartyRead: Boolean = false,
    val error: String? = null
)

private const val HISTORY_PAGE_SIZE = 50

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val appointmentRepository: AppointmentRepository,
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentRoomId: String? = null
    private var typingResetJob: Job? = null
    private var wasDisconnected = false
    private var currentPage = 0

    init {
        viewModelScope.launch {
            val myId = sessionDataStore.userId.first() ?: ""
            _uiState.update { it.copy(myUserId = myId) }
        }
    }

    fun initRoom(roomId: String) {
        if (currentRoomId == roomId) return
        currentRoomId = roomId

        loadOtherPartyName(roomId)
        // REST history is authoritative and loads before the live STOMP subscription starts.
        loadHistory(roomId)
        repository.connectWebSocket(roomId)

        viewModelScope.launch {
            repository.observeMessages(roomId).collect { incoming ->
                var isFromOtherParty = false
                _uiState.update { state ->
                    // Dedupe by id (STOMP echo vs. a REST refresh racing each other).
                    if (state.messages.any { it.id.isNotBlank() && it.id == incoming.id }) return@update state
                    val isMine = incoming.senderId == state.myUserId
                    isFromOtherParty = !isMine
                    state.copy(
                        messages = state.messages + incoming.copy(isMe = isMine),
                        // A message is only ever shown once the server confirms it via this
                        // echo - there is no optimistic bubble to reconcile, only this flag.
                        isSending = if (isMine) false else state.isSending,
                        // A fresh message from me hasn't been read yet; a fresh message from
                        // them doesn't change whether they've read *my* last message.
                        otherPartyRead = if (isMine) false else state.otherPartyRead
                    )
                }
                // The screen is open and a new message just arrived from the other party -
                // immediately mark the room read rather than waiting for the next visibility check.
                if (isFromOtherParty) repository.markRead(roomId)
            }
        }

        viewModelScope.launch {
            repository.observeTyping(roomId).collect { typing ->
                _uiState.update { it.copy(isOtherPartyTyping = typing) }
            }
        }

        viewModelScope.launch {
            val myId = sessionDataStore.userId.first()
            repository.observeRead(roomId).collect { readerId ->
                if (readerId != myId) {
                    _uiState.update { it.copy(otherPartyRead = true) }
                }
            }
        }

        viewModelScope.launch {
            repository.connectionState.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
                if (status == ConnectionStatus.CONNECTED && wasDisconnected) {
                    // Recovered from a dropped connection - resync from REST.
                    loadHistory(roomId)
                }
                wasDisconnected = status == ConnectionStatus.DISCONNECTED || status == ConnectionStatus.ERROR
            }
        }
    }

    /** Called when the chat screen becomes visible with messages already loaded. Best-effort, silently ignored if it fails. */
    fun markRoomRead(roomId: String) {
        repository.markRead(roomId)
    }

    private fun loadHistory(roomId: String) {
        currentPage = 0
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistory = true, error = null) }
            when (val result = repository.getMessageHistory(roomId, page = 0)) {
                is Result.Success -> {
                    val myId = _uiState.value.myUserId
                    // The backend returns each page newest-first (for easy "most recent N"
                    // pagination) - reverse so the in-memory list is chronological, oldest
                    // first, matching how a live STOMP message gets appended at the end.
                    val chronological = result.data.asReversed().map { it.copy(isMe = it.senderId == myId) }
                    _uiState.update { state ->
                        state.copy(
                            isLoadingHistory = false,
                            messages = chronological,
                            hasMoreHistory = result.data.size >= HISTORY_PAGE_SIZE
                        )
                    }
                }
                is Result.Error -> _uiState.update { it.copy(isLoadingHistory = false, error = result.exception.message) }
                is Result.Loading -> Unit
            }
        }
    }

    /** Fetches the next-older page and prepends it once the user scrolls to the top of history. */
    fun loadOlderMessages(roomId: String) {
        val state = _uiState.value
        if (state.isLoadingOlderMessages || !state.hasMoreHistory || state.isLoadingHistory) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOlderMessages = true) }
            val nextPage = currentPage + 1
            when (val result = repository.getMessageHistory(roomId, page = nextPage)) {
                is Result.Success -> {
                    currentPage = nextPage
                    val myId = _uiState.value.myUserId
                    val olderChronological = result.data.asReversed().map { it.copy(isMe = it.senderId == myId) }
                    _uiState.update { s ->
                        val existingIds = s.messages.mapNotNullTo(HashSet()) { it.id.ifBlank { null } }
                        val newOnes = olderChronological.filter { it.id.isBlank() || it.id !in existingIds }
                        s.copy(
                            isLoadingOlderMessages = false,
                            messages = newOnes + s.messages,
                            hasMoreHistory = result.data.size >= HISTORY_PAGE_SIZE
                        )
                    }
                }
                is Result.Error -> _uiState.update { it.copy(isLoadingOlderMessages = false, error = result.exception.message) }
                is Result.Loading -> Unit
            }
        }
    }

    private fun loadOtherPartyName(roomId: String) {
        val appointmentId = roomId.removePrefix("appointment_")
        viewModelScope.launch {
            val role = sessionDataStore.userRoleEnum.first()
            val myId = sessionDataStore.userId.first().orEmpty()
            appointmentRepository.getAppointmentById(appointmentId).onSuccess { appt ->
                val name = if (role == Role.DOCTOR) {
                    appt.patientName.ifBlank { "Patient" }
                } else {
                    appt.doctorName.ifBlank { "Doctor" }
                }
                val otherPartyId = if (myId.isNotBlank() && myId == appt.doctorUserId) {
                    appt.patientUserId
                } else {
                    appt.doctorUserId
                }
                val callable = appt.status == "CONFIRMED" || appt.status == "IN_PROGRESS"
                _uiState.update {
                    it.copy(
                        otherPartyName = name,
                        otherPartyUserId = otherPartyId,
                        canCall = callable && otherPartyId.isNotBlank()
                    )
                }
                Log.d(TAG, "Chat appointment=$appointmentId role=$role patient=${appt.patientName} doctor=${appt.doctorName} patientUser=${appt.patientUserId} doctorUser=${appt.doctorUserId} status=${appt.status} canCall=$callable")
            }
        }
    }

    fun sendMessage(roomId: String, text: String) {
        _uiState.update { it.copy(isSending = true, error = null, failedMessage = null) }

        viewModelScope.launch {
            val result = repository.sendMessage(roomId, text)
            if (result is Result.Error) {
                _uiState.update {
                    it.copy(isSending = false, error = result.exception.message, failedMessage = text)
                }
            }
            // On success the message only appears once the STOMP echo arrives via observeMessages -
            // there is no local bubble to reconcile, per the "no optimistic bubble" design here.
        }
    }

    /** Uploads [bytes] as an attachment, then sends it as a chat message once the upload succeeds. */
    fun sendAttachment(roomId: String, bytes: ByteArray, mimeType: String, filename: String) {
        if (_uiState.value.isUploadingAttachment) return // guards a rapid double-pick from firing two uploads
        _uiState.update { it.copy(isUploadingAttachment = true, error = null) }

        viewModelScope.launch {
            when (val uploadResult = repository.uploadMedia(roomId, bytes, mimeType, filename)) {
                is Result.Success -> {
                    val media = uploadResult.data
                    _uiState.update { it.copy(isUploadingAttachment = false) }
                    val sendResult = repository.sendMediaMessage(
                        roomId = roomId,
                        contentType = media.contentType,
                        mediaUrl = media.url,
                        content = media.originalFilename
                    )
                    if (sendResult is Result.Error) {
                        _uiState.update { it.copy(error = sendResult.exception.message) }
                    }
                    // On success the message only appears once the STOMP echo arrives, same as a text send.
                }
                is Result.Error -> _uiState.update { it.copy(isUploadingAttachment = false, error = uploadResult.exception.message) }
                is Result.Loading -> Unit
            }
        }
    }

    fun retryFailedMessage(roomId: String) {
        val text = _uiState.value.failedMessage ?: return
        sendMessage(roomId, text)
    }

    fun sendTypingIndicator(roomId: String, isTyping: Boolean) {
        typingResetJob?.cancel()
        val myId = _uiState.value.myUserId
        repository.sendTyping(roomId, myId, isTyping)
        if (isTyping) {
            typingResetJob = viewModelScope.launch {
                delay(3000)
                repository.sendTyping(roomId, myId, false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentRoomId?.let { repository.disconnectWebSocket(it) }
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
