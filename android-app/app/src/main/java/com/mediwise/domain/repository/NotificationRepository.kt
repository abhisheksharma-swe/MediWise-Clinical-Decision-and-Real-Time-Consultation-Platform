package com.mediwise.domain.repository

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Notification

interface NotificationRepository {
    suspend fun getNotifications(page: Int, size: Int): Result<List<Notification>>
    suspend fun markRead(id: String): Result<Unit>
    suspend fun markAllRead(): Result<Unit>
    suspend fun registerFcmToken(token: String, deviceId: String?, appVersion: String?): Result<Unit>
    suspend fun unregisterFcmToken(token: String?, deviceId: String?): Result<Unit>
}
