package com.mediwise.core.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/**
 * A lifecycle-safe STOMP-over-WebSocket connection manager.
 *
 * Responsibilities: real frame parsing (command + headers + body), a connection-state
 * StateFlow, a per-destination subscription registry with typed [StompFrame] flows,
 * bounded exponential backoff with jitter on unexpected disconnects, fresh token lookup
 * on every (re)connect attempt, and resubscription of all active destinations after a
 * reconnect. Callers never see raw text frames.
 */
@Singleton
class StompClient @Inject constructor(
    private val client: OkHttpClient
) {
    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    data class StompFrame(val command: String, val headers: Map<String, String>, val body: String)

    private data class Subscription(val id: String, val destination: String, val flow: MutableSharedFlow<StompFrame>)
    private data class PendingSend(val frame: String, val destination: String, val roomId: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var url: String = ""
    private var tokenProvider: (suspend () -> String?)? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var socketGeneration = 0L
    private var connectTimeoutJob: Job? = null
    @Volatile private var explicitlyDisconnected = true

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val lock = Any()
    private val subscriptionsByDestination = mutableMapOf<String, Subscription>()
    private val subscriptionsById = mutableMapOf<String, Subscription>()
    private val pendingSends = ArrayDeque<PendingSend>()

    /**
     * Opens the connection if not already open/opening for this [url]. [tokenProvider] is
     * invoked fresh on every connect and reconnect attempt so a refreshed session token is
     * always used - it is never captured once and reused.
     *
     * The backend's STOMP endpoint is registered with Spring's `.withSockJS()`, which only
     * performs a plain WebSocket upgrade for the literal path `{endpoint}/websocket` (Spring's
     * dedicated "raw WebSocket" SockJS transport, meant exactly for non-browser clients like
     * this OkHttp-based one that don't speak the SockJS framing protocol). Connecting straight
     * to the bare endpoint (e.g. `.../ws`) never upgrades to a WebSocket at all - the SockJS
     * HTTP handler just returns a plain response - so we normalize the URL here rather than
     * requiring every caller/config value to know about this transport suffix.
     */
    fun connect(url: String, tokenProvider: suspend () -> String?) {
        val normalizedUrl = if (url.endsWith("/websocket")) url else url.trimEnd('/') + "/websocket"
        if (!explicitlyDisconnected && webSocket != null && this.url == normalizedUrl) return // already connected/connecting
        socketGeneration++
        this.url = normalizedUrl
        this.tokenProvider = tokenProvider
        explicitlyDisconnected = false
        reconnectAttempt = 0
        reconnectJob?.cancel()
        openSocket()
    }

    private fun openSocket() {
        scope.launch {
            val generation = socketGeneration
            _connectionState.value = ConnectionState.Connecting
            Log.d(TAG, "STOMP_CONNECTING url=$url generation=$generation")
            val token = try {
                tokenProvider?.invoke()
            } catch (e: Exception) {
                null
            }

            val request = Request.Builder().url(url).build()
            val socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (generation != socketGeneration) {
                        webSocket.close(1000, "Superseded connection")
                        return
                    }
                    val headers = linkedMapOf("accept-version" to "1.1,1.0", "heart-beat" to "10000,10000")
                    if (!token.isNullOrBlank()) headers["Authorization"] = "Bearer $token"
                    Log.d(TAG, "STOMP_SOCKET_OPEN generation=$generation tokenPresent=${!token.isNullOrBlank()}")
                    webSocket.send(buildFrame("CONNECT", headers, ""))
                    connectTimeoutJob?.cancel()
                    connectTimeoutJob = scope.launch {
                        delay(CONNECT_TIMEOUT_MS)
                        if (generation == socketGeneration && _connectionState.value is ConnectionState.Connecting) {
                            Log.e(TAG, "STOMP_CONNECT_FAILED reason=CONNECT_TIMEOUT generation=$generation")
                            _connectionState.value = ConnectionState.Error("STOMP CONNECT timed out")
                            webSocket.close(4001, "STOMP CONNECT timeout")
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleFrame(text, generation)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleFrame(bytes.utf8(), generation)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.w(TAG, "STOMP_DISCONNECTED phase=CLOSING code=$code reason=$reason generation=$generation")
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (generation != socketGeneration) return
                    connectTimeoutJob?.cancel()
                    _connectionState.value = ConnectionState.Disconnected
                    Log.w(TAG, "STOMP_DISCONNECTED phase=CLOSED code=$code reason=$reason generation=$generation")
                    scheduleReconnectIfNeeded()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (generation != socketGeneration) return
                    connectTimeoutJob?.cancel()
                    Log.e(TAG, "STOMP_CONNECT_FAILED generation=$generation message=${t.message}", t)
                    _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
                    scheduleReconnectIfNeeded()
                }
            })
            webSocket = socket
        }
    }

    private fun handleFrame(text: String, generation: Long) {
        if (generation != socketGeneration) return
        val frame = parseFrame(text) ?: return // blank text is a heartbeat
        when (frame.command) {
            "CONNECTED" -> {
                connectTimeoutJob?.cancel()
                reconnectAttempt = 0
                _connectionState.value = ConnectionState.Connected
                Log.i(TAG, "STOMP_CONNECTED generation=$generation")
                resubscribeAll()
                synchronized(lock) {
                    while (pendingSends.isNotEmpty()) {
                        val pending = pendingSends.removeFirst()
                        webSocket?.send(pending.frame)
                        Log.d(TAG, "STOMP_SEND_SENT destination=${pending.destination} room=${pending.roomId}")
                    }
                }
            }
            "MESSAGE" -> {
                val subId = frame.headers["subscription"]
                val sub = synchronized(lock) { subId?.let { subscriptionsById[it] } }
                Log.d(TAG, "STOMP_MESSAGE_RECEIVED destination=${sub?.destination ?: "unknown"} room=${roomIdFrom(frame.body)}")
                sub?.flow?.tryEmit(frame)
            }
            "ERROR" -> {
                connectTimeoutJob?.cancel()
                Log.e(TAG, "STOMP_CONNECT_FAILED reason=ERROR message=${frame.headers["message"] ?: frame.body}")
                _connectionState.value = ConnectionState.Error(frame.headers["message"] ?: frame.body.ifBlank { "STOMP error" })
                webSocket?.close(4002, "STOMP ERROR")
            }
            "RECEIPT" -> Unit
        }
    }

    private fun parseFrame(raw: String): StompFrame? {
        val trimmed = raw.trimEnd('\u0000')
        if (trimmed.isBlank()) return null
        val splitIndex = trimmed.indexOf("\n\n")
        val headerPart = if (splitIndex != -1) trimmed.substring(0, splitIndex) else trimmed
        val body = if (splitIndex != -1) trimmed.substring(splitIndex + 2) else ""
        val lines = headerPart.split("\n")
        if (lines.isEmpty() || lines[0].isBlank()) return null
        val command = lines[0].trim()
        val headers = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
        }
        return StompFrame(command, headers, body)
    }

    private fun buildFrame(command: String, headers: Map<String, String>, body: String): String {
        val sb = StringBuilder(command).append('\n')
        headers.forEach { (k, v) -> sb.append(k).append(':').append(v).append('\n') }
        sb.append('\n').append(body).append('\u0000')
        return sb.toString()
    }

    /** Returns a flow of [StompFrame]s for [destination], subscribing once and reusing the flow for repeat calls. */
    fun subscribe(destination: String): Flow<StompFrame> {
        synchronized(lock) {
            subscriptionsByDestination[destination]?.let { return it.flow }
            val subId = "sub-${UUID.randomUUID()}"
            val flow = MutableSharedFlow<StompFrame>(replay = 0, extraBufferCapacity = 64)
            val subscription = Subscription(subId, destination, flow)
            subscriptionsByDestination[destination] = subscription
            subscriptionsById[subId] = subscription
            if (_connectionState.value is ConnectionState.Connected) sendSubscribe(subscription)
            Log.d(TAG, "STOMP_SUBSCRIBED destination=$destination")
            return flow
        }
    }

    fun unsubscribe(destination: String) {
        val subscription = synchronized(lock) {
            subscriptionsByDestination.remove(destination)?.also { subscriptionsById.remove(it.id) }
        } ?: return
        webSocket?.send(buildFrame("UNSUBSCRIBE", mapOf("id" to subscription.id), ""))
    }

    private fun sendSubscribe(subscription: Subscription) {
        webSocket?.send(buildFrame("SUBSCRIBE", mapOf("id" to subscription.id, "destination" to subscription.destination), ""))
        Log.d(TAG, "STOMP_SUBSCRIBED destination=${subscription.destination}")
    }

    private fun resubscribeAll() {
        synchronized(lock) { subscriptionsByDestination.values.toList() }.forEach { sendSubscribe(it) }
    }

    fun send(destination: String, body: String) {
        val frame = buildFrame("SEND", mapOf("destination" to destination, "content-type" to "application/json"), body)
        synchronized(lock) {
            if (_connectionState.value is ConnectionState.Connected && webSocket != null) {
                webSocket?.send(frame)
                Log.d(TAG, "STOMP_SEND_SENT destination=$destination room=${roomIdFrom(body)}")
            } else {
                if (pendingSends.size >= MAX_PENDING_SENDS) pendingSends.removeFirst()
                pendingSends.addLast(PendingSend(frame, destination, roomIdFrom(body)))
                Log.d(TAG, "STOMP_SEND_QUEUED destination=$destination room=${roomIdFrom(body)} state=${_connectionState.value}")
            }
        }
    }

    private fun scheduleReconnectIfNeeded() {
        if (explicitlyDisconnected) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempt++
            val exponent = min(reconnectAttempt, MAX_BACKOFF_EXPONENT)
            val backoffMs = min(MAX_BACKOFF_MS, (BASE_BACKOFF_MS * 2.0.pow(exponent - 1)).toLong())
            val jitterMs = (Math.random() * backoffMs * 0.3).toLong()
            Log.i(TAG, "STOMP_RECONNECTING attempt=$reconnectAttempt delayMs=${backoffMs + jitterMs}")
            delay(backoffMs + jitterMs)
            if (!explicitlyDisconnected) openSocket()
        }
    }

    private fun roomIdFrom(body: String): String =
        Regex("\\\"roomId\\\"\\s*:\\s*\\\"([^\\\"]+)").find(body)?.groupValues?.get(1) ?: "-"

    /** Explicit clean shutdown - call on logout. Stops any pending reconnect attempts. */
    fun disconnect() {
        explicitlyDisconnected = true
        reconnectJob?.cancel()
        webSocket?.send(buildFrame("DISCONNECT", emptyMap(), ""))
        webSocket?.close(1000, "Client disconnected")
        webSocket = null
        synchronized(lock) {
            subscriptionsByDestination.clear()
            subscriptionsById.clear()
            pendingSends.clear()
        }
        _connectionState.value = ConnectionState.Disconnected
    }

    companion object {
        private const val TAG = "StompClient"
        private const val BASE_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 30000L
        private const val MAX_BACKOFF_EXPONENT = 6
        private const val CONNECT_TIMEOUT_MS = 8000L
        private const val MAX_PENDING_SENDS = 200
    }
}
