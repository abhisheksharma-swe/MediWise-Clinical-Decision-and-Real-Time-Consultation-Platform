package com.mediwise.presentation.screens.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.result.onError
import com.mediwise.core.result.onSuccess
import com.mediwise.domain.model.Notification
import com.mediwise.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationUiState(
    val isLoading: Boolean = false,
    val notifications: List<Notification> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getNotifications(page = 0, size = 20)
                .onSuccess { list -> _uiState.update { it.copy(isLoading = false, notifications = list) } }
                .onError { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            repository.markRead(id)
            _uiState.update { state ->
                state.copy(notifications = state.notifications.map { if (it.id == id) it.copy(isRead = true) else it })
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            repository.markAllRead()
            _uiState.update { state ->
                state.copy(notifications = state.notifications.map { it.copy(isRead = true) })
            }
        }
    }
}
