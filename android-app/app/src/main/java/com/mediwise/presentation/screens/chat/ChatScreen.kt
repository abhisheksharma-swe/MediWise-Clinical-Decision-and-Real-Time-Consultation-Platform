package com.mediwise.presentation.screens.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.mediwise.domain.model.ChatContentType
import com.mediwise.domain.model.ConnectionStatus
import com.mediwise.presentation.theme.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Backend sends [sentAt]/`time` as an ISO-8601 UTC instant; renders blank on anything else (e.g. a not-yet-timestamped optimistic entry) rather than showing a raw ISO string. */
private fun formatMessageTime(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        Instant.parse(iso)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (e: Exception) {
        ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    roomId: String,
    onAudioCallClick: (otherPartyId: String, otherPartyName: String) -> Unit = { _, _ -> },
    onVideoCallClick: (otherPartyId: String, otherPartyName: String) -> Unit = { _, _ -> },
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var messageText by remember { mutableStateOf("") }

    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bytes = context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() }
                val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
                val filename = queryFileName(context, it) ?: "attachment"
                if (bytes != null) {
                    viewModel.sendAttachment(roomId, bytes, mimeType, filename)
                }
            } catch (_: Exception) {
                // Best-effort - a failed read here just means nothing gets sent; the user can retry the pick.
            }
        }
    }

    LaunchedEffect(roomId) { viewModel.initRoom(roomId) }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
            // The screen is visibly showing these messages now - let the other party know.
            viewModel.markRoomRead(roomId)
        }
    }

    // Scroll-up pagination: fetch the next-older page once the user nears the top of
    // what's currently loaded. `key = message.id` on the list below keeps this from
    // visually jumping when older messages get prepended.
    LaunchedEffect(listState, roomId) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstVisible ->
                if (firstVisible <= 2 && uiState.messages.isNotEmpty()) {
                    viewModel.loadOlderMessages(roomId)
                }
            }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    expandedHeight = 56.dp,
                    title = {
                        Column {
                            Text(uiState.otherPartyName.ifBlank { "Chat" }, fontWeight = FontWeight.SemiBold)
                            if (uiState.isOtherPartyTyping) {
                                Text("typing...", style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        if (uiState.canCall) {
                            IconButton(onClick = {
                                onAudioCallClick(uiState.otherPartyUserId, uiState.otherPartyName)
                            }) {
                                Icon(Icons.Default.Call, contentDescription = "Start audio call", tint = PrimaryBlue)
                            }
                            IconButton(
                                onClick = {
                                    onVideoCallClick(uiState.otherPartyUserId, uiState.otherPartyName)
                                },
                                enabled = false
                            ) {
                                Icon(Icons.Default.VideoCall, contentDescription = "Video calls unavailable", tint = TextSecondary)
                            }
                        }
                    }
                )
                if (uiState.connectionStatus != ConnectionStatus.CONNECTED) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(WarningAmberLight).padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (uiState.connectionStatus == ConnectionStatus.CONNECTING) "Connecting..." else "Reconnecting...",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.imePadding()) {
                if (uiState.failedMessage != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(ErrorRedLight).padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Message failed to send", style = MaterialTheme.typography.labelSmall, color = ErrorRed, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.retryFailedMessage(roomId) }) {
                            Text("Retry", color = ErrorRed, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceWhite)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { attachmentPickerLauncher.launch("*/*") },
                        enabled = !uiState.isUploadingAttachment
                    ) {
                        if (uiState.isUploadingAttachment) {
                            CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach file", tint = PrimaryBlue)
                        }
                    }
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = {
                            messageText = it
                            viewModel.sendTypingIndicator(roomId, it.isNotEmpty())
                        },
                        placeholder = { Text("Type a message...") },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge,
                        maxLines = 3
                    )
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank() && !uiState.isSending) {
                                viewModel.sendMessage(roomId, messageText.trim())
                                messageText = ""
                                viewModel.sendTypingIndicator(roomId, false)
                                scope.launch { listState.animateScrollToItem(uiState.messages.size) }
                            }
                        },
                        containerColor = PrimaryBlue,
                        modifier = Modifier.size(48.dp)
                    ) {
                        if (uiState.isSending) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.isLoadingHistory && uiState.messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.isLoadingOlderMessages) {
                item(key = "loading_older") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            val lastMyMessageId = uiState.messages.lastOrNull { it.senderId == uiState.myUserId }?.id

            items(uiState.messages, key = { it.id.ifBlank { "pending_${it.senderId}_${it.time}_${it.content.hashCode()}" } }) { message ->
                val isMe = message.senderId == uiState.myUserId
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        val bubbleShape = RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (isMe) 16.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 16.dp
                        )
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(bubbleShape)
                                .background(if (isMe) PrimaryBlue else SurfaceElevated)
                                .then(
                                    if (message.contentType == ChatContentType.IMAGE) Modifier
                                    else Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                when (message.contentType) {
                                    ChatContentType.IMAGE -> {
                                        AsyncImage(
                                            model = message.mediaUrl,
                                            contentDescription = message.content.ifBlank { "Image" },
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .clip(bubbleShape)
                                                .heightIn(max = 220.dp)
                                                .clickable {
                                                    message.mediaUrl?.let { url ->
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                    }
                                                }
                                        )
                                    }
                                    ChatContentType.FILE -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                                .clickable {
                                                    message.mediaUrl?.let { url ->
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                    }
                                                }
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.InsertDriveFile,
                                                contentDescription = null,
                                                tint = if (isMe) Color.White else PrimaryBlue,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = message.content.ifBlank { "Attachment" },
                                                color = if (isMe) Color.White else TextPrimary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.widthIn(max = 180.dp)
                                            )
                                        }
                                    }
                                    ChatContentType.TEXT -> {
                                        Text(
                                            text = message.content,
                                            color = if (isMe) Color.White else TextPrimary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                val time = formatMessageTime(message.time)
                                if (time.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = time,
                                        color = if (isMe) Color.White.copy(alpha = 0.7f) else TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = if (message.contentType == ChatContentType.IMAGE)
                                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp) else Modifier
                                    )
                                }
                            }
                        }
                    }
                    if (isMe && uiState.otherPartyRead && message.id.isNotBlank() && message.id == lastMyMessageId) {
                        Text(
                            "Read",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }

            if (uiState.isOtherPartyTyping) {
                item {
                    Row {
                        TypingBubble()
                    }
                }
            }
        }
    }
}

/** Resolves a content:// Uri's original display name (e.g. "report.pdf") for use as the attachment filename - falls back to null if the provider doesn't expose one. */
private fun queryFileName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun TypingBubble() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp))
            .background(SurfaceElevated)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("• • •", color = TextSecondary)
    }
}
