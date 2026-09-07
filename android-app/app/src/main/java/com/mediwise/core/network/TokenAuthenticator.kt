    package com.mediwise.core.network

import com.mediwise.BuildConfig
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.data.remote.dto.ApiResponseDto
import com.mediwise.data.remote.dto.AuthResponseDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionDataStore: SessionDataStore
) : Authenticator {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2 || response.request.url.encodedPath.contains("auth/refresh") || response.request.url.encodedPath.contains("auth/login")) {
            return null
        }

        synchronized(this) {
            val currentToken = runBlocking { sessionDataStore.accessToken.first() }
            val authHeader = response.request.header("Authorization")

            // If token has already been refreshed by another thread while waiting, retry with that
            if (authHeader != null && currentToken != null && authHeader != "Bearer $currentToken") {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = runBlocking { sessionDataStore.refreshToken.first() }
            if (refreshToken.isNullOrBlank()) {
                return null
            }

            return try {
                val refreshClient = OkHttpClient.Builder().build()
                val refreshRequest = Request.Builder()
                    .url(BuildConfig.BASE_URL + "api/v1/auth/refresh")
                    .post("".toRequestBody("application/json".toMediaType()))
                    .addHeader("X-Refresh-Token", refreshToken)
                    .build()

                val refreshResponse = refreshClient.newCall(refreshRequest).execute()
                if (!refreshResponse.isSuccessful) {
                    runBlocking { sessionDataStore.clearSession() }
                    return null
                }

                val responseBody = refreshResponse.body?.string() ?: return null
                val authResult = json.decodeFromString<ApiResponseDto<AuthResponseDto>>(responseBody)
                val data = authResult.data ?: return null

                runBlocking {
                    sessionDataStore.saveSession(
                        data.accessToken,
                        data.refreshToken,
                        data.user.id,
                        data.user.role,
                        data.user.email
                    )
                }

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${data.accessToken}")
                    .build()
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
