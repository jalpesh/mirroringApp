package com.example.mirroringapp.mirroring

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Application.settingsDataStore by preferencesDataStore(name = "mirroring_settings")

class MirroringController(private val app: Application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val projectionFactory = MirroringIntentFactory(app)

    suspend fun initialise() {
        // Warm up the datastore to avoid initial lag when toggling options.
        app.settingsDataStore.data.first()
    }

    suspend fun setSelectedConnection(option: ConnectionOption) {
        app.settingsDataStore.edit { prefs ->
            prefs[SELECTED_CONNECTION] = option.name
        }
    }

    suspend fun setLowLatencyEnabled(enabled: Boolean) {
        app.settingsDataStore.edit { prefs ->
            prefs[LOW_LATENCY] = enabled
        }
    }

    suspend fun setHardwareEncoderEnabled(enabled: Boolean) {
        app.settingsDataStore.edit { prefs ->
            prefs[HARDWARE_ENCODER] = enabled
        }
    }

    suspend fun getSelectedConnection(): ConnectionOption {
        val stored = app.settingsDataStore.data
            .map { prefs -> prefs[SELECTED_CONNECTION]?.let { ConnectionOption.valueOf(it) } }
            .first()
        return stored ?: ConnectionOption.USB_C
    }

    suspend fun isLowLatencyEnabled(): Boolean {
        return app.settingsDataStore.data
            .map { prefs -> prefs[LOW_LATENCY] ?: true }
            .first()
    }

    suspend fun isHardwareEncoderEnabled(): Boolean {
        return app.settingsDataStore.data
            .map { prefs -> prefs[HARDWARE_ENCODER] ?: true }
            .first()
    }

    fun createProjectionIntent(): Intent? {
        return projectionFactory.createProjectionIntent()
    }

    suspend fun startMirroring(resultCode: Int, data: Intent) {
        scope.launch {
            val serviceIntent = MirroringService.createStartIntent(
                context = app,
                resultCode = resultCode,
                projectionData = data,
                connectionOption = getSelectedConnection(),
                lowLatency = isLowLatencyEnabled(),
                hardwareEncoder = isHardwareEncoderEnabled()
            )
            app.startForegroundServiceCompat(serviceIntent)
        }
    }

    suspend fun stopMirroring() {
        scope.launch {
            app.stopService(MirroringService.createStopIntent(app))
        }
    }

    private fun Context.startForegroundServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    companion object {
        private val SELECTED_CONNECTION = stringPreferencesKey("selected_connection")
        private val LOW_LATENCY = booleanPreferencesKey("low_latency")
        private val HARDWARE_ENCODER = booleanPreferencesKey("hardware_encoder")
    }
}
