package com.mediwise.presentation.screens.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediwise.domain.model.CallDirection
import com.mediwise.domain.model.CallMediaType
import com.mediwise.domain.model.CallState
import com.mediwise.presentation.theme.*

@Composable
fun CallScreen(
    roomId: String,
    otherPartyId: String,
    otherPartyName: String,
    mediaType: CallMediaType,
    direction: CallDirection,
    onCallEnded: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var micPermanentlyDenied by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (!granted) micPermanentlyDenied = true
    }

    // Set up once per screen instance - both branches are idempotent against a re-trigger
    // (initRoom-style dedupe lives in CallViewModel), so recomposition is safe.
    LaunchedEffect(roomId, direction) {
        if (direction == CallDirection.INCOMING) {
            viewModel.showIncomingRinging(roomId, otherPartyId, mediaType)
        }
    }

    LaunchedEffect(uiState.state) {
        if (uiState.state == CallState.ENDED || uiState.state == CallState.FAILED) {
            onCallEnded()
        }
    }

    // Outgoing calls (and an incoming call once accepted) need the mic before WebRTC can
    // start - request it here rather than at CallViewModel, which has no Activity context.
    LaunchedEffect(direction, uiState.hasAccepted) {
        val needsMic = direction == CallDirection.OUTGOING || uiState.hasAccepted
        if (needsMic && !micGranted) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(micGranted, direction) {
        if (micGranted && direction == CallDirection.OUTGOING && uiState.state == CallState.IDLE) {
            viewModel.startOutgoingCall(roomId, otherPartyId, mediaType)
        }
    }

    Scaffold(containerColor = Color(0xFF111827)) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !micGranted && (direction == CallDirection.OUTGOING || uiState.hasAccepted) -> {
                    MicPermissionGate(
                        permanentlyDenied = micPermanentlyDenied,
                        onRequest = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onCancel = onCallEnded
                    )
                }

                direction == CallDirection.INCOMING && !uiState.hasAccepted -> {
                    IncomingCallContent(
                        callerName = otherPartyName,
                        mediaType = mediaType,
                        onAccept = { viewModel.acceptIncomingCall() },
                        onDecline = {
                            viewModel.rejectIncomingCall()
                            onCallEnded()
                        }
                    )
                }

                else -> {
                    InCallContent(
                        otherPartyName = otherPartyName,
                        state = uiState.state,
                        isMuted = uiState.isMuted,
                        elapsedSeconds = uiState.elapsedSeconds,
                        error = uiState.error,
                        onToggleMute = { viewModel.toggleMute() },
                        onEndCall = { viewModel.endCall() }
                    )
                }
            }
        }
    }
}

@Composable
private fun MicPermissionGate(permanentlyDenied: Boolean, onRequest: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.MicOff, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Microphone access needed", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            if (permanentlyDenied)
                "Microphone permission was denied. Enable it from system Settings to make calls."
            else
                "MediWise needs microphone access to place or receive calls.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(24.dp))
        if (!permanentlyDenied) {
            Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                Text("Allow Microphone")
            }
            Spacer(Modifier.height(12.dp))
        }
        TextButton(onClick = onCancel) {
            Text("Cancel", color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun IncomingCallContent(
    callerName: String,
    mediaType: CallMediaType,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CallerAvatar(callerName)
            Spacer(Modifier.height(20.dp))
            Text(callerName.ifBlank { "Unknown" }, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (mediaType == CallMediaType.VIDEO) "Incoming video call..." else "Incoming call...",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallActionButton(icon = Icons.Default.CallEnd, label = "Decline", background = ErrorRed, onClick = onDecline)
            CallActionButton(icon = Icons.Default.Call, label = "Accept", background = AccentGreen, onClick = onAccept)
        }
    }
}

@Composable
private fun InCallContent(
    otherPartyName: String,
    state: CallState,
    isMuted: Boolean,
    elapsedSeconds: Int,
    error: String?,
    onToggleMute: () -> Unit,
    onEndCall: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CallerAvatar(otherPartyName)
            Spacer(Modifier.height(20.dp))
            Text(otherPartyName.ifBlank { "Unknown" }, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (state) {
                    CallState.RINGING -> "Calling..."
                    CallState.CONNECTING -> "Connecting..."
                    CallState.CONNECTED -> formatElapsed(elapsedSeconds)
                    CallState.ENDED -> error ?: "Call ended"
                    CallState.FAILED -> error ?: "Call failed"
                    CallState.IDLE -> ""
                },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp
            )
            if (state == CallState.CONNECTED && error != null) {
                Spacer(Modifier.height(4.dp))
                Text(error, color = WarningAmber, fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallActionButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                background = Color.White.copy(alpha = 0.15f),
                onClick = onToggleMute
            )
            CallActionButton(icon = Icons.Default.CallEnd, label = "End", background = ErrorRed, onClick = onEndCall)
        }
    }
}

@Composable
private fun CallerAvatar(name: String) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier.size(96.dp).clip(CircleShape).background(PrimaryBlue.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CallActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, background: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = background,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
