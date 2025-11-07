package com.example.mirroringapp

import com.example.mirroringapp.mirroring.ConnectionOption

/**
 * Represents the immutable UI model consumed by the Compose layer. The UI reacts to
 * preference changes as well as the current mirroring status exposed by the service layer.
 */
data class MirroringUiState(
    val preferences: MirroringPreferences = MirroringPreferences(),
    val status: MirroringStatus = MirroringStatus.Idle
) {
    val isMirroring: Boolean get() = status is MirroringStatus.Mirroring
}

/**
 * Persisted mirroring configuration that the user can tweak from the home screen.
 */
data class MirroringPreferences(
    val connectionOption: ConnectionOption = ConnectionOption.USB_C,
    val lowLatencyEnabled: Boolean = true,
    val hardwareEncoderEnabled: Boolean = true
)

/**
 * Discrete states that describe what the mirroring workflow is currently doing. Keeping the
 * states explicit avoids impossible combinations such as "loading and mirroring" at the same
 * time while also giving the UI enough information to display progress and errors.
 */
sealed interface MirroringStatus {
    data object Idle : MirroringStatus
    data object RequestingPermission : MirroringStatus
    data object Starting : MirroringStatus
    data class Mirroring(val connection: ConnectionOption) : MirroringStatus
    data class Error(val message: String) : MirroringStatus
}
