package com.example.mirroringapp

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mirroringapp.data.repository.SettingsRepository
import com.example.mirroringapp.domain.usecase.GetProjectionIntentUseCase
import com.example.mirroringapp.domain.usecase.StartMirroringUseCase
import com.example.mirroringapp.domain.usecase.StopMirroringUseCase
import com.example.mirroringapp.mirroring.ConnectionOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MirroringViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val startMirroringUseCase: StartMirroringUseCase,
    private val stopMirroringUseCase: StopMirroringUseCase,
    private val getProjectionIntentUseCase: GetProjectionIntentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<MirroringUiState>(MirroringUiState.Idle())
    val uiState: StateFlow<MirroringUiState> = _uiState.asStateFlow()

    fun initialize() {
        viewModelScope.launch {
            try {
                settingsRepository.initialize()
                val connection = settingsRepository.getConnectionOption()
                val lowLatency = settingsRepository.isLowLatencyEnabled()
                val hwEncoder = settingsRepository.isHardwareEncoderEnabled()
                
                _uiState.value = MirroringUiState.Idle(
                    connectionOption = connection,
                    lowLatencyEnabled = lowLatency,
                    hardwareEncoderEnabled = hwEncoder
                )
                Timber.i("Initialization complete")
            } catch (e: Exception) {
                Timber.e(e, "Initialization failed")
                _uiState.value = MirroringUiState.Error(
                    connectionOption = ConnectionOption.USB_C,
                    lowLatencyEnabled = true,
                    hardwareEncoderEnabled = true,
                    message = "Failed to initialize: ${e.message}"
                )
            }
        }
    }

    fun setConnectionOption(option: ConnectionOption) {
        viewModelScope.launch {
            try {
                settingsRepository.setConnectionOption(option)
                _uiState.update { current ->
                    when (current) {
                        is MirroringUiState.Idle -> current.copy(connectionOption = option)
                        is MirroringUiState.Error -> MirroringUiState.Idle(
                            connectionOption = option,
                            lowLatencyEnabled = current.lowLatencyEnabled,
                            hardwareEncoderEnabled = current.hardwareEncoderEnabled
                        )
                        else -> current
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set connection option")
            }
        }
    }

    fun setLowLatencyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setLowLatencyEnabled(enabled)
                _uiState.update { current ->
                    when (current) {
                        is MirroringUiState.Idle -> current.copy(lowLatencyEnabled = enabled)
                        is MirroringUiState.Error -> current.copy(lowLatencyEnabled = enabled)
                        else -> current
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set low latency")
            }
        }
    }

    fun setHardwareEncoderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setHardwareEncoderEnabled(enabled)
                _uiState.update { current ->
                    when (current) {
                        is MirroringUiState.Idle -> current.copy(hardwareEncoderEnabled = enabled)
                        is MirroringUiState.Error -> current.copy(hardwareEncoderEnabled = enabled)
                        else -> current
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set hardware encoder")
            }
        }
    }

    fun requestProjectionPermission(launcher: (Intent) -> Unit) {
        try {
            val current = _uiState.value
            _uiState.value = MirroringUiState.RequestingPermission(
                connectionOption = current.connectionOption,
                lowLatencyEnabled = current.lowLatencyEnabled,
                hardwareEncoderEnabled = current.hardwareEncoderEnabled
            )
            
            val projectionIntent = getProjectionIntentUseCase()
            if (projectionIntent != null) {
                launcher(projectionIntent)
            } else {
                _uiState.value = MirroringUiState.Error(
                    connectionOption = current.connectionOption,
                    lowLatencyEnabled = current.lowLatencyEnabled,
                    hardwareEncoderEnabled = current.hardwareEncoderEnabled,
                    message = "Failed to create projection intent"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to request permission")
            val current = _uiState.value
            _uiState.value = MirroringUiState.Error(
                connectionOption = current.connectionOption,
                lowLatencyEnabled = current.lowLatencyEnabled,
                hardwareEncoderEnabled = current.hardwareEncoderEnabled,
                message = "Permission request failed: ${e.message}"
            )
        }
    }

    fun startMirroring(resultCode: Int, intent: Intent) {
        viewModelScope.launch {
            try {
                val current = _uiState.value
                _uiState.value = MirroringUiState.Starting(
                    connectionOption = current.connectionOption,
                    lowLatencyEnabled = current.lowLatencyEnabled,
                    hardwareEncoderEnabled = current.hardwareEncoderEnabled
                )
                
                val result = startMirroringUseCase(resultCode, intent)
                
                if (result.isSuccess) {
                    _uiState.value = MirroringUiState.Mirroring(
                        connectionOption = current.connectionOption,
                        lowLatencyEnabled = current.lowLatencyEnabled,
                        hardwareEncoderEnabled = current.hardwareEncoderEnabled,
                        displayInfo = "Connected via ${current.connectionOption}"
                    )
                    Timber.i("Mirroring started successfully")
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start mirroring")
                val current = _uiState.value
                _uiState.value = MirroringUiState.Error(
                    connectionOption = current.connectionOption,
                    lowLatencyEnabled = current.lowLatencyEnabled,
                    hardwareEncoderEnabled = current.hardwareEncoderEnabled,
                    message = "Failed to start mirroring: ${e.message}",
                    canRetry = true
                )
            }
        }
    }

    fun stopMirroring() {
        viewModelScope.launch {
            try {
                stopMirroringUseCase()
                val current = _uiState.value
                _uiState.value = MirroringUiState.Idle(
                    connectionOption = current.connectionOption,
                    lowLatencyEnabled = current.lowLatencyEnabled,
                    hardwareEncoderEnabled = current.hardwareEncoderEnabled
                )
                Timber.i("Mirroring stopped")
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop mirroring")
            }
        }
    }

    fun onProjectionDenied() {
        val current = _uiState.value
        _uiState.value = MirroringUiState.Error(
            connectionOption = current.connectionOption,
            lowLatencyEnabled = current.lowLatencyEnabled,
            hardwareEncoderEnabled = current.hardwareEncoderEnabled,
            message = "Screen capture permission denied",
            canRetry = true
        )
        Timber.w("Projection permission denied")
    }

    fun retryAfterError() {
        val current = _uiState.value
        _uiState.value = MirroringUiState.Idle(
            connectionOption = current.connectionOption,
            lowLatencyEnabled = current.lowLatencyEnabled,
            hardwareEncoderEnabled = current.hardwareEncoderEnabled
        )
    }

}
