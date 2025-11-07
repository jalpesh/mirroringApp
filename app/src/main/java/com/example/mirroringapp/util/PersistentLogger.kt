package com.example.mirroringapp.util

import android.content.Context
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent logger that saves logs to device storage.
 * Logs can be retrieved later for debugging.
 */
object PersistentLogger {
    
    private const val LOG_FILE_NAME = "mirroring_debug.log"
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5MB
    
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    fun initialize(context: Context) {
        logFile = File(context.getExternalFilesDir(null), LOG_FILE_NAME)
        log("INFO", "=== MIRRORING APP STARTED ===")
        log("INFO", "Log file: ${logFile?.absolutePath}")
        log("INFO", "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        log("INFO", "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
    }
    
    fun log(level: String, message: String) {
        try {
            val file = logFile ?: return
            
            // Rotate log if too large
            if (file.exists() && file.length() > MAX_LOG_SIZE) {
                val backup = File(file.parent, "${LOG_FILE_NAME}.old")
                file.renameTo(backup)
            }
            
            val timestamp = dateFormat.format(Date())
            val logLine = "$timestamp [$level] $message\n"
            
            file.appendText(logLine)
            
            // Also log to Timber
            when (level) {
                "ERROR" -> Timber.e(message)
                "WARN" -> Timber.w(message)
                "INFO" -> Timber.i(message)
                "DEBUG" -> Timber.d(message)
                else -> Timber.v(message)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to write to log file")
        }
    }
    
    fun d(message: String) = log("DEBUG", message)
    fun i(message: String) = log("INFO", message)
    fun w(message: String) = log("WARN", message)
    fun e(message: String) = log("ERROR", message)
    
    fun getLogFilePath(): String? = logFile?.absolutePath
    
    fun getLogContent(): String {
        return try {
            logFile?.readText() ?: "No logs available"
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }
    
    fun clearLogs() {
        try {
            logFile?.delete()
            log("INFO", "=== LOGS CLEARED ===")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear logs")
        }
    }
    
    fun exportLogs(): File? = logFile
}
