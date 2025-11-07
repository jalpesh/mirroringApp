package com.example.mirroringapp

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.mirroringapp.mirroring.ConnectionOption
import com.example.mirroringapp.mirroring.MirroringController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MirroringViewModel(private val app: Application) : AndroidViewModel(app) {

    private val controller = MirroringController(app)

    private val _uiState = MutableStateFlow(MirroringUiState())
    val uiState: StateFlow<MirroringUiState> = _uiState.asStateFlow()

    suspend fun ensureInitialisation() {
        controller.initialise()
        val preferences = MirroringPreferences(
            connectionOption = controller.getSelectedConnection(),
            lowLatencyEnabled = controller.isLowLatencyEnabled(),
            hardwareEncoderEnabled = controller.isHardwareEncoderEnabled()
        )
        _uiState.update { it.copy(preferences = preferences, status = MirroringStatus.Idle) }
    }

    suspend fun setConnectionOption(option: ConnectionOption) {
        controller.setSelectedConnection(option)
        _uiState.update { state ->
            state.copy(preferences = state.preferences.copy(connectionOption = option))
        }
    }

    suspend fun setLowLatencyEnabled(enabled: Boolean) {
        controller.setLowLatencyEnabled(enabled)
        _uiState.update { state ->
            state.copy(preferences = state.preferences.copy(lowLatencyEnabled = enabled))
        }
    }

    suspend fun setHardwareEncoderEnabled(enabled: Boolean) {
        controller.setHardwareEncoderEnabled(enabled)
        _uiState.update { state ->
            state.copy(preferences = state.preferences.copy(hardwareEncoderEnabled = enabled))
        }
    }

    suspend fun requestProjectionPermission(launcher: (Intent) -> Unit) {
        val projectionIntent = controller.createProjectionIntent()
        if (projectionIntent == null) {
            setError(app.getString(R.string.error_projection_unavailable))
            return
        }
        _uiState.update { it.copy(status = MirroringStatus.RequestingPermission) }
        launcher(projectionIntent)
    }

    suspend fun startMirroring(resultCode: Int, intent: Intent) {
        _uiState.update { it.copy(status = MirroringStatus.Starting) }
        val connection = _uiState.value.preferences.connectionOption
        runCatching {
            controller.startMirroring(resultCode, intent)
        }.onSuccess {
            _uiState.update { current ->
                current.copy(status = MirroringStatus.Mirroring(connection))
            }
        }.onFailure { throwable ->
            val detail = throwable.localizedMessage?.takeIf { it.isNotBlank() }
            val message = if (detail != null) {
                app.getString(R.string.error_start_mirroring_with_detail, detail)
            } else {
                app.getString(R.string.error_start_mirroring_generic)
            }
            setError(message)
        }
    }

    suspend fun stopMirroring() {
        runCatching { controller.stopMirroring() }
        _uiState.update { it.copy(status = MirroringStatus.Idle) }
    }

    fun onProjectionDenied() {
        setError(app.getString(R.string.error_projection_permission_denied))
    }

    fun clearError() {
        _uiState.update { state ->
            if (state.status is MirroringStatus.Error) {
                state.copy(status = MirroringStatus.Idle)
            } else {
                state
            }
        }
    }

    private fun setError(message: String) {
        _uiState.update { it.copy(status = MirroringStatus.Error(message)) }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MirroringViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MirroringViewModel(app) as T
            }
            throw IllegalArgumentException("Unknown ViewModel")
        }
    }
}
