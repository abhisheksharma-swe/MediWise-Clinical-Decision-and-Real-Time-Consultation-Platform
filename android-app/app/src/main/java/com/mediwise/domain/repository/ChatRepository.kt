package com.mediwise.domain.repository

import com.mediwise.core.result.Result
import com.mediwise.domain.model.ChatContentType
import com.mediwise.domain.model.ChatMessage
import com.mediwise.domain.model.ConnectionStatus
import kotlinx.coroutines.flow.Flow

data class ChatMediaUploadResult(val url: String, val contentType: ChatContentType, val originalFilename: String)

interface ChatRepository {
    val connectionState: Flow<ConnectionStatus>
    fun observeMessages(roomId: String): Flow<ChatMessage>
    fun observeTyping(roomId: String): Flow<Boolean>
    /** Emits the userId of whoever just marked [roomId] as read (via [markRead]) - including echoes of this user's own read events. */
    fun observeRead(roomId: String): Flow<String>
    suspend fun getMessageHistory(roomId: String, page: Int = 0): Result<List<ChatMessage>>
    suspend fun sendMessage(roomId: String, content: String): Result<Unit>
    /** Sends an already-uploaded attachment as a chat message - [content] is an optional caption. */
    suspend fun sendMediaMessage(roomId: String, contentType: ChatContentType, mediaUrl: String, content: String = ""): Result<Unit>
    /** Uploads a file/image to be sent as the next chat message; does not itself post anything to the room. */
    suspend fun uploadMedia(roomId: String, bytes: ByteArray, mimeType: String, filename: String): Result<ChatMediaUploadResult>
    fun sendTyping(roomId: String, senderId: String, isTyping: Boolean)
    fun markRead(roomId: String)
    fun connectWebSocket(roomId: String)
    fun disconnectWebSocket(roomId: String)
}
