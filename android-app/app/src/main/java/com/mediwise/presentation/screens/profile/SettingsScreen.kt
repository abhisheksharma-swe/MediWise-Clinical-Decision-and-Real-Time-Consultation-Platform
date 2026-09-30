package com.mediwise.presentation.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val sessionDataStore: SessionDataStore
) : ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val sessionDataStore = viewModel.sessionDataStore
    val coroutineScope = rememberCoroutineScope()

    val pushNotifications by sessionDataStore.pushNotifications.collectAsState(initial = true)
    val appointmentReminders by sessionDataStore.apptReminders.collectAsState(initial = true)
    val chatNotifications by sessionDataStore.chatNotifications.collectAsState(initial = true)
    val marketingEmails by sessionDataStore.marketingEmails.collectAsState(initial = false)
    val biometricLogin by sessionDataStore.biometricLogin.collectAsState(initial = false)
    val darkMode by sessionDataStore.darkMode.collectAsState(initial = false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWhite)
            )
        },
        containerColor = BackgroundWhite
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SettingsSection(title = "Notifications & Reminders") {
                    ToggleSetting(
                        icon = Icons.Default.Notifications,
                        title = "Push Notifications",
                        subtitle = "Get alerts for appointments and updates",
                        checked = pushNotifications,
                        onCheckedChange = {
                            coroutineScope.launch { sessionDataStore.saveSetting("push", it) }
                        }
                    )
                    HorizontalDivider(color = Divider)
                    ToggleSetting(
                        icon = Icons.Default.Alarm,
                        title = "Appointment Reminders",
                        subtitle = "1 hour before your consultation",
                        checked = appointmentReminders,
                        onCheckedChange = {
                            coroutineScope.launch { sessionDataStore.saveSetting("reminders", it) }
                        }
                    )
                    HorizontalDivider(color = Divider)
                    ToggleSetting(
                        icon = Icons.Default.Chat,
                        title = "Chat Notifications",
                        subtitle = "New messages from doctors",
                        checked = chatNotifications,
                        onCheckedChange = {
                            coroutineScope.launch { sessionDataStore.saveSetting("chat", it) }
                        }
                    )
                    HorizontalDivider(color = Divider)
                    ToggleSetting(
                        icon = Icons.Default.Email,
                        title = "Marketing Emails",
                        subtitle = "Health tips and promotions",
                        checked = marketingEmails,
                        onCheckedChange = {
                            coroutineScope.launch { sessionDataStore.saveSetting("marketing", it) }
                        }
                    )
                }
            }

            item {
                SettingsSection(title = "Security & Access") {
                    ToggleSetting(
                        icon = Icons.Default.Fingerprint,
                        title = "Biometric Login",
                        subtitle = "Use fingerprint or face unlock",
                        checked = biometricLogin,
                        onCheckedChange = {
                            coroutineScope.launch { sessionDataStore.saveSetting("biometric", it) }
                        }
                    )
                    HorizontalDivider(color = Divider)
                    ActionSetting(
                        icon = Icons.Default.Lock,
                        title = "Privacy Policy & Terms",
                        subtitle = "Review MediWise clinical data privacy"
                    )
                }
            }

            item {
                SettingsSection(title = "Appearance") {
                    ToggleSetting(
                        icon = Icons.Default.DarkMode,
                        title = "Dark Mode",
                        subtitle = "Toggle dark theme for night usage",
                        checked = darkMode,
                        onCheckedChange = {
                            coroutineScope.launch { sessionDataStore.saveSetting("dark", it) }
                        }
                    )
                }
            }

            item {
                SettingsSection(title = "App Info & Support") {
                    ActionSetting(
                        icon = Icons.Default.Info,
                        title = "App Version",
                        subtitle = "MediWise v1.0.0 (Production Build)"
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun ToggleSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = PrimaryBlueLight, modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = SurfaceWhite, checkedTrackColor = PrimaryBlue)
        )
    }
}

@Composable
private fun ActionSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isDestructive) ErrorRed.copy(alpha = 0.1f) else PrimaryBlueLight,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon, contentDescription = null,
                    tint = if (isDestructive) ErrorRed else PrimaryBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = if (isDestructive) ErrorRed else TextPrimary
            )
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Icon(
            Icons.Default.ChevronRight, contentDescription = null,
            tint = TextSecondary, modifier = Modifier.size(18.dp)
        )
    }
}

