package com.example.mirroringapp.di

import android.app.Application
import android.content.Context
import com.example.mirroringapp.data.repository.MirroringRepository
import com.example.mirroringapp.data.repository.MirroringRepositoryImpl
import com.example.mirroringapp.data.repository.SettingsRepository
import com.example.mirroringapp.data.repository.SettingsRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(application: Application): Context {
        return application.applicationContext
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository {
        return SettingsRepositoryImpl(context)
    }

    @Provides
    @Singleton
    fun provideMirroringRepository(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): MirroringRepository {
        return MirroringRepositoryImpl(context, settingsRepository)
    }
}
