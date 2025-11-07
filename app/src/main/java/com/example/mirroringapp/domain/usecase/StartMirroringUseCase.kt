package com.example.mirroringapp.domain.usecase

import android.content.Intent
import com.example.mirroringapp.data.repository.MirroringRepository
import com.example.mirroringapp.data.repository.SettingsRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for starting screen mirroring.
 * Encapsulates business logic for mirroring startup.
 */
class StartMirroringUseCase @Inject constructor(
    private val mirroringRepository: MirroringRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(resultCode: Int, data: Intent): Result<Unit> {
        return try {
            val connectionOption = settingsRepository.getConnectionOption()
            val lowLatency = settingsRepository.isLowLatencyEnabled()
            val hardwareEncoder = settingsRepository.isHardwareEncoderEnabled()
            
            Timber.i("Starting mirroring: connection=$connectionOption, lowLatency=$lowLatency, hwEncoder=$hardwareEncoder")
            
            mirroringRepository.startMirroring(
                resultCode = resultCode,
                data = data,
                connectionOption = connectionOption,
                lowLatency = lowLatency,
                hardwareEncoder = hardwareEncoder
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start mirroring")
            Result.failure(e)
        }
    }
}
