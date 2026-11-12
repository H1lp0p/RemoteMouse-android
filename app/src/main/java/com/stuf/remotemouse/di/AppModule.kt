package com.stuf.remotemouse.di

import android.content.Context
import androidx.room.Room
import com.stuf.remotemouse.data.local.AppDatabase
import com.stuf.remotemouse.data.local.MIGRATION_2_3
import com.stuf.remotemouse.data.local.MIGRATION_3_4
import com.stuf.remotemouse.data.local.MIGRATION_4_5
import com.stuf.remotemouse.data.local.dao.ActionDao
import com.stuf.remotemouse.data.local.dao.ActionPadDao
import com.stuf.remotemouse.data.local.dao.ActionStepDao
import com.stuf.remotemouse.data.local.dao.MeDao
import com.stuf.remotemouse.data.local.dao.PcDeviceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "remote_mouse.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    fun provideMeDao(db: AppDatabase): MeDao = db.meDao()

    @Provides
    fun providePcDeviceDao(db: AppDatabase): PcDeviceDao = db.pcDeviceDao()

    @Provides
    fun provideActionDao(db: AppDatabase): ActionDao = db.actionDao()

    @Provides
    fun provideActionStepDao(db: AppDatabase): ActionStepDao = db.actionStepDao()

    @Provides
    fun provideActionPadDao(db: AppDatabase): ActionPadDao = db.actionPadDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofitBuilder(okHttpClient: OkHttpClient, json: Json): Retrofit.Builder {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
    }
}
