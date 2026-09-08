package com.mediwise.core.fcm

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result as WorkResult
import androidx.work.WorkerParameters
import com.mediwise.BuildConfig
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.domain.repository.NotificationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Registers a pending FCM token with the backend once the user is authenticated.
 * Enqueued by [ClinicalFcmService.onNewToken] rather than calling the network directly
 * from that short-lived callback. Uses WorkManager's built-in backoff for retries and
 * never logs the token value itself.
 */
@HiltWorker
class FcmTokenRegistrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notificationRepository: NotificationRepository,
    private val sessionDataStore: SessionDataStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): WorkResult {
        val pendingToken = sessionDataStore.pendingFcmToken.first()
            ?: return WorkResult.success() // Nothing pending - already registered or never set.

        val isLoggedIn = sessionDataStore.isLoggedIn.first()
        if (!isLoggedIn) {
            // Wait for the user to authenticate; WorkManager will re-run this with backoff.
            return WorkResult.retry()
        }

        val deviceId = sessionDataStore.getOrCreateDeviceId()
        val result = notificationRepository.registerFcmToken(
            token = pendingToken,
            deviceId = deviceId,
            appVersion = BuildConfig.VERSION_NAME
        )
        if (result is com.mediwise.core.result.Result.Success) {
            sessionDataStore.markFcmTokenRegistered(pendingToken)
            return WorkResult.success()
        }
        if (result is com.mediwise.core.result.Result.Error) {
            Log.w(TAG, "FCM token registration failed, will retry: ${result.exception.message}")
        }
        return WorkResult.retry()
    }

    companion object {
        const val WORK_NAME = "fcm_token_registration"
        private const val TAG = "FcmTokenWorker"
    }
}
