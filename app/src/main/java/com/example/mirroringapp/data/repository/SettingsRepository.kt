package com.example.mirroringapp.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mirroringapp.mirroring.ConnectionOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val Context.settingsDataStore by preferencesDataStore(name = "mirroring_settings")

/**
 * Repository for managing app settings using DataStore.
 * Provides clean abstraction over persistence layer.
 */
interface SettingsRepository {
    suspend fun setConnectionOption(option: ConnectionOption)
    suspend fun getConnectionOption(): ConnectionOption
    fun getConnectionOptionFlow(): Flow<ConnectionOption>
    
    suspend fun setLowLatencyEnabled(enabled: Boolean)
    suspend fun isLowLatencyEnabled(): Boolean
    fun getLowLatencyFlow(): Flow<Boolean>
    
    suspend fun setHardwareEncoderEnabled(enabled: Boolean)
    suspend fun isHardwareEncoderEnabled(): Boolean
    fun getHardwareEncoderFlow(): Flow<Boolean>
    
    suspend fun initialize()
}

class SettingsRepositoryImpl(
    private val context: Context
) : SettingsRepository {

    private val dataStore = context.settingsDataStore

    override suspend fun initialize() {
        // Warm up the datastore
        dataStore.data.first()
        Timber.d("Settings repository initialized")
    }

    override suspend fun setConnectionOption(option: ConnectionOption) {
        dataStore.edit { prefs ->
            prefs[KEY_CONNECTION] = option.name
        }
        Timber.d("Connection option set to: $option")
    }

    override suspend fun getConnectionOption(): ConnectionOption {
        return dataStore.data
            .map { prefs -> 
                prefs[KEY_CONNECTION]?.let { ConnectionOption.valueOf(it) } ?: DEFAULT_CONNECTION
            }
            .first()
    }

    override fun getConnectionOptionFlow(): Flow<ConnectionOption> {
        return dataStore.data.map { prefs ->
            prefs[KEY_CONNECTION]?.let { ConnectionOption.valueOf(it) } ?: DEFAULT_CONNECTION
        }
    }

    override suspend fun setLowLatencyEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_LOW_LATENCY] = enabled
        }
        Timber.d("Low latency set to: $enabled")
    }

    override suspend fun isLowLatencyEnabled(): Boolean {
        return dataStore.data
            .map { prefs -> prefs[KEY_LOW_LATENCY] ?: DEFAULT_LOW_LATENCY }
            .first()
    }

    override fun getLowLatencyFlow(): Flow<Boolean> {
        return dataStore.data.map { prefs ->
            prefs[KEY_LOW_LATENCY] ?: DEFAULT_LOW_LATENCY
        }
    }

    override suspend fun setHardwareEncoderEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HARDWARE_ENCODER] = enabled
        }
        Timber.d("Hardware encoder set to: $enabled")
    }

    override suspend fun isHardwareEncoderEnabled(): Boolean {
        return dataStore.data
            .map { prefs -> prefs[KEY_HARDWARE_ENCODER] ?: DEFAULT_HARDWARE_ENCODER }
            .first()
    }

    override fun getHardwareEncoderFlow(): Flow<Boolean> {
        return dataStore.data.map { prefs ->
            prefs[KEY_HARDWARE_ENCODER] ?: DEFAULT_HARDWARE_ENCODER
        }
    }

    companion object {
        private val KEY_CONNECTION = stringPreferencesKey("selected_connection")
        private val KEY_LOW_LATENCY = booleanPreferencesKey("low_latency")
        private val KEY_HARDWARE_ENCODER = booleanPreferencesKey("hardware_encoder")
        
        private val DEFAULT_CONNECTION = ConnectionOption.USB_C
        private const val DEFAULT_LOW_LATENCY = true
        private const val DEFAULT_HARDWARE_ENCODER = true
    }
}
