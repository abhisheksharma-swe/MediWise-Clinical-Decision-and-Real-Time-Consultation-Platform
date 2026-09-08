package com.mediwise.core.network

import com.mediwise.BuildConfig
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.domain.model.RealtimeNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app-wide `/user/queue/notifications` subscription, kept separate from any
 * per-appointment chat subscription. Started once after login, stopped on logout.
 * Emits realtime notification events deduplicated by their persisted id so the same
 * event delivered via STOMP and (later, redundantly) FCM/REST is only counted once.
 */
@Singleton
class AppNotificationSocket @Inject constructor(
    private val stompClient: StompClient,
    private val sessionDataStore: SessionDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectJob: Job? = null
    private val seenIds = Collections.synchronizedSet(mutableSetOf<String>())

    private val _events = MutableSharedFlow<RealtimeNotification>(extraBufferCapacity = 32)
    val events: SharedFlow<RealtimeNotification> = _events

    fun start() {
        if (collectJob != null) return
        stompClient.connect(BuildConfig.WS_URL) { sessionDataStore.accessToken.first() }
        collectJob = scope.launch {
            stompClient.subscribe(DESTINATION).collect { frame ->
                val notification = parse(frame.body) ?: return@collect
                if (notification.id.isNotBlank() && !seenIds.add(notification.id)) return@collect
                _events.tryEmit(notification)
            }
        }
    }

    /** Stops the subscription and fully closes the STOMP connection - call on logout. */
    fun stop() {
        collectJob?.cancel()
        collectJob = null
        seenIds.clear()
        stompClient.disconnect()
    }

    private fun parse(body: String): RealtimeNotification? {
        return try {
            val obj = JSONObject(body)
            RealtimeNotification(
                id = obj.optString("id", ""),
                type = obj.optString("type", "GENERAL"),
                title = obj.optString("title", ""),
                body = obj.optString("body", ""),
                refId = obj.optString("refId", "").ifBlank { null }
            )
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val DESTINATION = "/user/queue/notifications"
    }
}
