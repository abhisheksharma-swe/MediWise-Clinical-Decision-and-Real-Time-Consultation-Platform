package com.mediwise.core.di

import com.mediwise.BuildConfig
import com.mediwise.core.network.AuthInterceptor
import com.mediwise.core.network.ErrorInterceptor
import com.mediwise.core.network.TokenAuthenticator
import com.mediwise.data.remote.api.*
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        errorInterceptor: ErrorInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(errorInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG)
                    HttpLoggingInterceptor.Level.BODY
                else
                    HttpLoggingInterceptor.Level.NONE
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides @Singleton fun provideAuthApi(r: Retrofit): AuthApi = r.create(AuthApi::class.java)
    @Provides @Singleton fun provideDoctorApi(r: Retrofit): DoctorApi = r.create(DoctorApi::class.java)
    @Provides @Singleton fun provideAppointmentApi(r: Retrofit): AppointmentApi = r.create(AppointmentApi::class.java)
    @Provides @Singleton fun providePaymentApi(r: Retrofit): PaymentApi = r.create(PaymentApi::class.java)
    @Provides @Singleton fun provideProfileApi(r: Retrofit): ProfileApi = r.create(ProfileApi::class.java)
    @Provides @Singleton fun provideNotificationApi(r: Retrofit): NotificationApi = r.create(NotificationApi::class.java)
    @Provides @Singleton fun provideChatApi(r: Retrofit): ChatApi = r.create(ChatApi::class.java)
    @Provides @Singleton fun provideSlotApi(r: Retrofit): SlotApi = r.create(SlotApi::class.java)
    @Provides @Singleton fun provideAiApi(r: Retrofit): AiApi = r.create(AiApi::class.java)
}
