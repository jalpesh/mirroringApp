package com.example.mirroringapp.domain.usecase

import com.example.mirroringapp.data.repository.MirroringRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for stopping screen mirroring.
 */
class StopMirroringUseCase @Inject constructor(
    private val mirroringRepository: MirroringRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            Timber.i("Stopping mirroring")
            mirroringRepository.stopMirroring()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop mirroring")
            Result.failure(e)
        }
    }
}
