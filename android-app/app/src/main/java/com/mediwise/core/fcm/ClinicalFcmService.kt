package com.mediwise.core.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mediwise.R
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.presentation.MainActivity
import com.mediwise.presentation.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class ClinicalFcmService : FirebaseMessagingService() {

    @Inject lateinit var sessionDataStore: SessionDataStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /**
     * Never logs the token itself - only that a refresh happened. The token is persisted
     * as pending and handed to WorkManager so the actual network call survives this
     * short-lived callback and gets retried with backoff.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        serviceScope.launch {
            sessionDataStore.savePendingFcmToken(token)
            enqueueRegistration()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        serviceScope.launch {
            handleMessage(message)
        }
    }

    private suspend fun handleMessage(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: "GENERAL"
        val title = data["title"] ?: message.notification?.title ?: "MediWise"
        val body = data["body"] ?: message.notification?.body ?: ""
        val refId = data["refId"]
        val notificationId = data["notificationId"]

        if (!sessionDataStore.pushNotifications.first()) return

        val category = categoryFor(type)
        val categoryEnabled = when (category) {
            Category.APPOINTMENT -> sessionDataStore.apptReminders.first()
            Category.CHAT -> sessionDataStore.chatNotifications.first()
            Category.GENERAL -> true
        }
        if (!categoryEnabled) return

        showNotification(
            channelId = if (category == Category.CHAT) CHANNEL_CHAT else CHANNEL_APPOINTMENTS,
            title = title,
            body = body,
            deepLinkRoute = deepLinkRouteFor(type, refId),
            notificationKey = notificationId ?: refId ?: System.currentTimeMillis().toString()
        )
    }

    private fun categoryFor(type: String): Category = when {
        type.startsWith("APPOINTMENT") || type == "NEW_APPOINTMENT" -> Category.APPOINTMENT
        type.contains("CHAT") || type.contains("MESSAGE") -> Category.CHAT
        else -> Category.GENERAL
    }

    private fun deepLinkRouteFor(type: String, refId: String?): String {
        if (refId.isNullOrBlank()) return Screen.Notifications.route
        return when {
            type.startsWith("APPOINTMENT") || type == "NEW_APPOINTMENT" ->
                Screen.AppointmentDetail.createRoute(refId)
            type.contains("CHAT") || type.contains("MESSAGE") ->
                Screen.Chat.createRoute("appointment_$refId")
            else -> Screen.Notifications.route
        }
    }

    private fun showNotification(channelId: String, title: String, body: String, deepLinkRoute: String, notificationKey: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DEEP_LINK_ROUTE, deepLinkRoute)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(notificationKey.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing, skipping display")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_APPOINTMENTS, "Appointments", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Booking, confirmation, and status updates for your appointments"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHAT, "Chat", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New messages from doctors and patients"
            }
        )
    }

    private fun enqueueRegistration() {
        val request = OneTimeWorkRequestBuilder<FcmTokenRegistrationWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, androidx.work.WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(FcmTokenRegistrationWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private enum class Category { APPOINTMENT, CHAT, GENERAL }

    companion object {
        private const val TAG = "ClinicalFcmService"
        const val CHANNEL_APPOINTMENTS = "appointments"
        const val CHANNEL_CHAT = "chat"
    }
}
