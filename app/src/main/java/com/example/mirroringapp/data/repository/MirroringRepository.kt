package com.example.mirroringapp.data.repository

import android.content.Context
import android.content.Intent
import com.example.mirroringapp.mirroring.ConnectionOption
import com.example.mirroringapp.mirroring.MirroringIntentFactory
import com.example.mirroringapp.mirroring.MirroringService
import timber.log.Timber

/**
 * Repository for managing mirroring operations.
 * Abstracts service communication and intent creation.
 */
interface MirroringRepository {
    fun createProjectionIntent(): Intent?
    suspend fun startMirroring(
        resultCode: Int,
        data: Intent,
        connectionOption: ConnectionOption,
        lowLatency: Boolean,
        hardwareEncoder: Boolean
    )
    suspend fun stopMirroring()
}

class MirroringRepositoryImpl(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) : MirroringRepository {

    private val projectionFactory = MirroringIntentFactory(context)

    override fun createProjectionIntent(): Intent? {
        return try {
            projectionFactory.createProjectionIntent().also {
                Timber.d("Created projection intent")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create projection intent")
            null
        }
    }

    override suspend fun startMirroring(
        resultCode: Int,
        data: Intent,
        connectionOption: ConnectionOption,
        lowLatency: Boolean,
        hardwareEncoder: Boolean
    ) {
        try {
            val serviceIntent = MirroringService.createStartIntent(
                context = context,
                resultCode = resultCode,
                projectionData = data,
                connectionOption = connectionOption,
                lowLatency = lowLatency,
                hardwareEncoder = hardwareEncoder
            )
            
            context.startForegroundService(serviceIntent)
            Timber.i("Mirroring service started: $connectionOption")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start mirroring service")
            throw e
        }
    }

    override suspend fun stopMirroring() {
        try {
            context.stopService(MirroringService.createStopIntent(context))
            Timber.i("Mirroring service stopped")
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop mirroring service")
            throw e
        }
    }
}
