package com.example.mirroringapp

import com.example.mirroringapp.mirroring.ConnectionOption

/**
 * Sealed interface for mutually exclusive UI states.
 * Prevents impossible states like "loading and error at the same time".
 */
sealed interface MirroringUiState {
    val connectionOption: ConnectionOption
    val lowLatencyEnabled: Boolean
    val hardwareEncoderEnabled: Boolean

    data class Idle(
        override val connectionOption: ConnectionOption = ConnectionOption.USB_C,
        override val lowLatencyEnabled: Boolean = true,
        override val hardwareEncoderEnabled: Boolean = true
    ) : MirroringUiState

    data class RequestingPermission(
        override val connectionOption: ConnectionOption = ConnectionOption.USB_C,
        override val lowLatencyEnabled: Boolean = true,
        override val hardwareEncoderEnabled: Boolean = true
    ) : MirroringUiState

    data class Starting(
        override val connectionOption: ConnectionOption,
        override val lowLatencyEnabled: Boolean,
        override val hardwareEncoderEnabled: Boolean
    ) : MirroringUiState

    data class Mirroring(
        override val connectionOption: ConnectionOption,
        override val lowLatencyEnabled: Boolean,
        override val hardwareEncoderEnabled: Boolean,
        val displayInfo: String? = null
    ) : MirroringUiState

    data class Error(
        override val connectionOption: ConnectionOption,
        override val lowLatencyEnabled: Boolean,
        override val hardwareEncoderEnabled: Boolean,
        val message: String,
        val canRetry: Boolean = true
    ) : MirroringUiState
}
