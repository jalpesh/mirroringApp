package com.example.mirroringapp.domain.usecase

import android.content.Intent
import com.example.mirroringapp.data.repository.MirroringRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for creating MediaProjection intent.
 */
class GetProjectionIntentUseCase @Inject constructor(
    private val mirroringRepository: MirroringRepository
) {
    operator fun invoke(): Intent? {
        return mirroringRepository.createProjectionIntent().also {
            if (it == null) {
                Timber.w("Failed to create projection intent")
            }
        }
    }
}
