package com.mediwise.data.repository

import com.mediwise.BuildConfig
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.core.network.StompClient
import com.mediwise.core.network.safeApiCall
import com.mediwise.core.result.AppException
import com.mediwise.core.result.Result
import com.mediwise.data.remote.api.ChatApi
import com.mediwise.data.remote.dto.StompSendMessageDto
import com.mediwise.data.remote.dto.StompTypingDto
import com.mediwise.data.remote.dto.toDomain
import com.mediwise.domain.model.ChatContentType
import com.mediwise.domain.model.ChatMessage
import com.mediwise.domain.model.ConnectionStatus
import com.mediwise.domain.repository.ChatMediaUploadResult
import com.mediwise.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val stompClient: StompClient,
    private val chatApi: ChatApi,
    private val sessionDataStore: SessionDataStore
) : ChatRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override val connectionState: Flow<ConnectionStatus> = stompClient.connectionState.map {
        when (it) {
            is StompClient.ConnectionState.Connected -> ConnectionStatus.CONNECTED
            is StompClient.ConnectionState.Connecting -> ConnectionStatus.CONNECTING
            is StompClient.ConnectionState.Disconnected -> ConnectionStatus.DISCONNECTED
            is StompClient.ConnectionState.Error -> ConnectionStatus.ERROR
        }
    }

    override fun connectWebSocket(roomId: String) {
        stompClient.connect(BuildConfig.WS_URL) { sessionDataStore.accessToken.first() }
    }

    override fun observeMessages(roomId: String): Flow<ChatMessage> {
        return stompClient.subscribe("/topic/chat/$roomId").mapNotNull { frame -> parseMessage(frame.body) }
    }

    override fun observeTyping(roomId: String): Flow<Boolean> {
        return stompClient.subscribe("/topic/chat/$roomId/typing").mapNotNull { frame ->
            try {
                JSONObject(frame.body).optBoolean("typing", false)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun getMessageHistory(roomId: String, page: Int): Result<List<ChatMessage>> {
        return safeApiCall {
            val response = chatApi.getMessages(roomId, page)
            response.data?.content?.map { it.toDomain() } ?: emptyList()
        }
    }

    override suspend fun sendMessage(roomId: String, content: String): Result<Unit> {
        return try {
            val payload = json.encodeToString(StompSendMessageDto.serializer(), StompSendMessageDto(roomId = roomId, content = content))
            stompClient.send("/app/chat.send", payload)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(AppException.UnknownException(e.localizedMessage ?: "Failed to send message"))
        }
    }

    override suspend fun sendMediaMessage(roomId: String, contentType: ChatContentType, mediaUrl: String, content: String): Result<Unit> {
        return try {
            val payload = json.encodeToString(
                StompSendMessageDto.serializer(),
                StompSendMessageDto(roomId = roomId, content = content, contentType = contentType.name, mediaUrl = mediaUrl)
            )
            stompClient.send("/app/chat.send", payload)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(AppException.UnknownException(e.localizedMessage ?: "Failed to send attachment"))
        }
    }

    override suspend fun uploadMedia(roomId: String, bytes: ByteArray, mimeType: String, filename: String): Result<ChatMediaUploadResult> {
        return safeApiCall {
            val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", filename, requestBody)
            val data = chatApi.uploadMedia(roomId, part).data ?: throw Exception("Empty upload response")
            ChatMediaUploadResult(
                url = data.url,
                contentType = ChatContentType.entries.firstOrNull { it.name == data.contentType } ?: ChatContentType.FILE,
                originalFilename = data.originalFilename ?: filename
            )
        }
    }

    override fun sendTyping(roomId: String, senderId: String, isTyping: Boolean) {
        try {
            val payload = json.encodeToString(StompTypingDto.serializer(), StompTypingDto(roomId = roomId, senderId = senderId, typing = isTyping))
            stompClient.send("/app/chat.typing", payload)
        } catch (e: Exception) {
            // Typing indicators are best-effort; never surface an error for this.
        }
    }

    override fun observeRead(roomId: String): Flow<String> {
        return stompClient.subscribe("/topic/chat/$roomId/read").mapNotNull { frame ->
            // Backend broadcasts the reader's raw userId as a JSON string body (@Payload String) - unwrap it.
            try {
                json.decodeFromString(String.serializer(), frame.body)
            } catch (e: Exception) {
                frame.body.trim('"').ifBlank { null }
            }
        }
    }

    override fun markRead(roomId: String) {
        try {
            // /app/chat.read's handler is @Payload String roomId, not a DTO - the body must
            // still be valid JSON (StompClient always sends content-type: application/json),
            // so a bare roomId string needs to be JSON-encoded (quoted), not sent raw.
            stompClient.send("/app/chat.read", json.encodeToString(String.serializer(), roomId))
        } catch (e: Exception) {
            // Read receipts are best-effort; never surface an error for this.
        }
    }

    override fun disconnectWebSocket(roomId: String) {
        stompClient.unsubscribe("/topic/chat/$roomId")
        stompClient.unsubscribe("/topic/chat/$roomId/typing")
        stompClient.unsubscribe("/topic/chat/$roomId/read")
    }

    private fun parseMessage(body: String): ChatMessage? {
        return try {
            val obj = JSONObject(body)
            val contentType = ChatContentType.entries.firstOrNull { it.name == obj.optString("contentType", "TEXT") } ?: ChatContentType.TEXT
            ChatMessage(
                id = obj.optString("id", ""),
                senderId = obj.optString("senderId", ""),
                content = obj.optString("content", ""),
                time = obj.optString("sentAt", ""),
                isMe = false,
                contentType = contentType,
                mediaUrl = obj.optString("mediaUrl").ifBlank { null }
            )
        } catch (e: Exception) {
            null
        }
    }
}
