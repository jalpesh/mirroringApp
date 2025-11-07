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
        _uiState.update { current ->
            current.copy(
                connectionOption = controller.getSelectedConnection(),
                lowLatencyEnabled = controller.isLowLatencyEnabled(),
                hardwareEncoderEnabled = controller.isHardwareEncoderEnabled()
            )
        }
    }

    suspend fun setConnectionOption(option: ConnectionOption) {
        controller.setSelectedConnection(option)
        _uiState.update { it.copy(connectionOption = option) }
    }

    suspend fun setLowLatencyEnabled(enabled: Boolean) {
        controller.setLowLatencyEnabled(enabled)
        _uiState.update { it.copy(lowLatencyEnabled = enabled) }
    }

    suspend fun setHardwareEncoderEnabled(enabled: Boolean) {
        controller.setHardwareEncoderEnabled(enabled)
        _uiState.update { it.copy(hardwareEncoderEnabled = enabled) }
    }

    suspend fun requestProjectionPermission(launcher: (Intent) -> Unit) {
        val projectionIntent = controller.createProjectionIntent() ?: return
        launcher(projectionIntent)
    }

    suspend fun startMirroring(resultCode: Int, intent: Intent) {
        controller.startMirroring(resultCode, intent)
        _uiState.update { it.copy(isMirroring = true) }
    }

    suspend fun stopMirroring() {
        controller.stopMirroring()
        _uiState.update { it.copy(isMirroring = false) }
    }

    fun onProjectionDenied() {
        _uiState.update { it.copy(isMirroring = false) }
    }

    data class MirroringUiState(
        val connectionOption: ConnectionOption = ConnectionOption.USB_C,
        val lowLatencyEnabled: Boolean = true,
        val hardwareEncoderEnabled: Boolean = true,
        val isMirroring: Boolean = false
    )

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
